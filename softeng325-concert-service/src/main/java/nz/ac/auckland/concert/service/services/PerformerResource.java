package nz.ac.auckland.concert.service.services;

import nz.ac.auckland.concert.common.dto.PerformerDTO;
import nz.ac.auckland.concert.common.message.Messages;
import nz.ac.auckland.concert.service.domain.Mappers.PerformerMapper;
import nz.ac.auckland.concert.service.domain.Performer;
import nz.ac.auckland.concert.service.domain.Token;
import nz.ac.auckland.concert.service.domain.Types.SubscriptionType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;
import javax.persistence.TypedQuery;
import javax.ws.rs.*;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Path("/performers")
public class PerformerResource {

    private static final Logger _logger = LoggerFactory.getLogger(PerformerResource.class);

    @Context // Information about the service hosted URI
    private static UriInfo _uri;

    private final PersistenceManager _pm; // Persistence
    private final SubscriptionManager _sm; // Subscription management

    public PerformerResource() {

        _pm = PersistenceManager.instance();
        _sm = SubscriptionManager.instance();
    }

    /**
     * Retrieves a single performer given by id
     * @param userAgent
     * @param id
     * @return PerformerDTO
     */
    @GET
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_XML)
    public Response getPerformer(
            @HeaderParam("user-agent") String userAgent,
            @PathParam("id") long id) {

        EntityManager em = _pm.createEntityManager();

        try{
            Performer performer = em.find(Performer.class, id);
            PerformerDTO returnPerformer = PerformerMapper.toDto(performer);

            return Response
                    .status(Response.Status.OK)
                    .entity(returnPerformer)
                    .build();
        } finally {
            em.close();
        }
    }

    /**
     * This method allows for multiple performers to be retrieved in batches up to the clients discretion.
     * No authentication is required here.
     * @param userAgent
     * @param start
     * @param size
     * @return list of performers with uri for next batch
     */
    @GET
    @Path("/")
    @Produces(MediaType.APPLICATION_XML)
    public Response getPerformers(
            @HeaderParam("user-agent") String userAgent,
            @DefaultValue("0") @QueryParam("start") int start,
            @DefaultValue("10") @QueryParam("size") int size) {

        EntityManager em = _pm.createEntityManager();

        try {
            TypedQuery<Performer> q = em.createQuery("SELECT p FROM Performer p", Performer.class);
            List<Performer> performers = q.setFirstResult(start).setMaxResults(size).getResultList();

            List<PerformerDTO> performerDTOs = performers.stream().map(PerformerMapper::toDto).collect(Collectors.toList());
            GenericEntity<List<PerformerDTO>> entity = new GenericEntity<List<PerformerDTO>>(performerDTOs) {};
            _logger.info("Retrieved (" + performers.size() + ") performers; send to user agent: " + userAgent);

            return Response
                    .status(Response.Status.OK)
                    .location(new URI(_uri.getBaseUri() + String.format("performers?start=%d&size=%d", start + size, size))) // Next batch of performers
                    .entity(entity)
                    .build();
        } catch (URISyntaxException e) {
            _logger.info("Denied user agent: " + userAgent + "; could not convert return URI");
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        } finally {
            em.close();
        }
    }

    /**
     * Creates a new performer entity in the database of the service. Authentication is required and can be
     * provided through an authorization token.
     * @param performerDTO
     * @param userAgent
     * @param authToken
     * @return URI to created performer object
     */
    @POST
    @Path("/")
    public Response addPerformer(
            PerformerDTO performerDTO,
            @HeaderParam("user-agent") String userAgent,
            @HeaderParam("Authorization") String authToken) {

        if (authToken == null) { // User has no access token
            _logger.info("Denied user agent: " + userAgent + "; No authentication token identified.");
            return Response.status(Response.Status.FORBIDDEN).entity(Messages.UNAUTHENTICATED_REQUEST).build();
        }

        if (performerDTO.getName() == null) { // Any necessary fields are missing
            _logger.info("Denied user agent: " + userAgent + "; With missing field(s) in performerDTO.");
            return Response.status(Response.Status.BAD_REQUEST).entity(Messages.RESERVATION_REQUEST_WITH_MISSING_FIELDS).build(); // Bad request
        }

        EntityManager em = _pm.createEntityManager();

        try {
            EntityTransaction tx = em.getTransaction();
            tx.begin();

            if (!tokenIsValid(authToken, em)) { // If token wasn't found or is expired return unauthorized
                _logger.info("Denied user agent : " + userAgent + "; With expired/invalid authentication token: " + authToken);
                return Response.status(Response.Status.UNAUTHORIZED).entity(Messages.BAD_AUTHENTICATON_TOKEN).build();
            }

            Performer newPerformer = PerformerMapper.toDomainModel(performerDTO);
            em.persist(newPerformer);

            tx.commit();
            _logger.info("Successfully created new performer with id: " + newPerformer.getId() + " and name: " + newPerformer.getName());

            _sm.notifySubscribers(SubscriptionType.PERFORMER, newPerformer, _uri.getBaseUri() + "performers/" + newPerformer.getId());
            _logger.info("Subscribers notified of new performer: " + newPerformer.getName());

            return Response
                    .status(Response.Status.OK)
                    .location(new URI(_uri.getBaseUri() + "performers/" + newPerformer.getId()))
                    .build();
        } catch (URISyntaxException e) {
            _logger.info("Denied user agent: " + userAgent + "; could not convert return URI");
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        } finally {
            em.close();
        }
    }

    /**
     * Subscribes a user to notifications related to ANY new performer added to the database
     * @param response
     * @param userAgent
     * @param authToken
     */
    @GET
    @Path("/getNotifications")
    @Consumes(MediaType.APPLICATION_XML)
    public void waitForNewPerformers(
            @Suspended AsyncResponse response,
            @HeaderParam("user-agent") String userAgent,
            @HeaderParam("Authorization") String authToken) {

        if (authToken == null) { // User has no access token
            _logger.info("Denied user agent: " + userAgent + "; No authentication token identified.");
            response.resume(Messages.UNAUTHENTICATED_REQUEST);
        }

        _sm.addSubscription(SubscriptionType.PERFORMER, response);
        _logger.info("Subscriber added for new performers");
    }


    // Private methods


    private boolean tokenIsValid(String authToken, EntityManager em) {

        // Retrieve corresponding token form the database
        TypedQuery<Token> tokenQuery = em.createQuery("SELECT t FROM Token t WHERE t.token = :token", Token.class);
        tokenQuery.setParameter("token", authToken);
        Token token = tokenQuery.getSingleResult();

        // True if token isn't null and its expiry time is after the current time
        return token != null && !LocalDateTime.now().isAfter(token.getExpiry());
    }
}
