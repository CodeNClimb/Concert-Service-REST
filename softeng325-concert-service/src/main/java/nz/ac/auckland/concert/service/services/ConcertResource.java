package nz.ac.auckland.concert.service.services;

import nz.ac.auckland.concert.common.dto.ConcertDTO;
import nz.ac.auckland.concert.common.message.Messages;
import nz.ac.auckland.concert.service.domain.Concert;
import nz.ac.auckland.concert.service.domain.Mappers.ConcertMapper;
import nz.ac.auckland.concert.service.domain.Performer;
import nz.ac.auckland.concert.service.domain.Token;
import nz.ac.auckland.concert.service.domain.Types.SubscriptionType;
import nz.ac.auckland.concert.service.domain.User;
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
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Path("/concerts")
public class ConcertResource {

    private static final Logger _logger = LoggerFactory.getLogger(ConcertResource.class);

    @Context // Information about the service hosted URI
    private static UriInfo _uri;

    private final PersistenceManager _pm; // Persistence
    private final SubscriptionManager _sm; // Subscription management

    public ConcertResource() {

        _pm = PersistenceManager.instance();
        _sm = SubscriptionManager.instance();
    }

    /**
     * Retrieves a single concert given an id
     * @param userAgent
     * @param id
     * @return ConcertDTO
     */
    @GET
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_XML)
    public Response getConcert(
            @HeaderParam("user-agent") String userAgent,
            @PathParam("id") long id) {

        EntityManager em = _pm.createEntityManager();

        try {
            Concert concert = em.find(Concert.class, id);
            ConcertDTO returnConcert = ConcertMapper.toDto(concert);

            return Response
                    .status(Response.Status.OK)
                    .entity(returnConcert)
                    .build();
        } finally {
            em.close();
        }
    }

    /**
     * This method allows for multiple concerts to be retrieved in batches up to the clients discretion.
     * No authentication is required here.
     * @param userAgent
     * @param start
     * @param size
     * @return list of concerts with uri for next batch
     */
    @GET
    @Path("/")
    @Produces(MediaType.APPLICATION_XML)
    public Response getConcerts(
            @HeaderParam("user-agent") String userAgent,
            @QueryParam("start") int start,
            @QueryParam("size") int size) {

        EntityManager em = _pm.createEntityManager();

        try {
            TypedQuery<Concert> q = em.createQuery("SELECT c FROM Concert c", Concert.class);
            List<Concert> concerts = q.setFirstResult(start).setMaxResults(size).getResultList();

            List<ConcertDTO> concertDTOs = concerts.stream().map(ConcertMapper::toDto).collect(Collectors.toList());
            GenericEntity<List<ConcertDTO>> entity = new GenericEntity<List<ConcertDTO>>(concertDTOs) {};
            _logger.info("Retrieved (" + concerts.size() + ") concerts; send to user agent: " + userAgent);

            return Response
                    .status(Response.Status.OK)
                    .location(new URI(_uri.getBaseUri() + String.format("concerts?start=%d&size=%d", start + size, size))) // next batch of concerts
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
     * Creates a new concert entity in the database of the service. Authentication is required and can be
     * provided through an authorization token.
     * @param concertDTO
     * @param userAgent
     * @param authToken
     * @return URI to created concert
     */
    @POST
    @Path("/")
    public Response addConcert(
            ConcertDTO concertDTO,
            @HeaderParam("user-agent") String userAgent,
            @HeaderParam("Authorization") String authToken) {

        if (authToken == null) { // User has no access token
            _logger.info("Denied user agent: " + userAgent + "; No authentication token identified.");
            return Response.status(Response.Status.FORBIDDEN).entity(Messages.UNAUTHENTICATED_REQUEST).build();
        }

        if (concertDTO.getTitle() == null || concertDTO.getDates() == null || concertDTO.getDates().isEmpty() ||
                concertDTO.getPerformerIds() == null || concertDTO.getPerformerIds().isEmpty()) { // Any necessary fields are missing
            _logger.info("Denied user agent: " + userAgent + "; With missing field(s) in concertDTO.");
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

            Concert newConcert = ConcertMapper.toDomainModel(concertDTO);
            newConcert = em.merge(newConcert);

            tx.commit();
            _logger.info("Successfully created new concert with id: " + newConcert.getId() + ", name: " + newConcert.getTitle() +
                    " and performers: " + Arrays.toString(newConcert.getPerformers().stream().map(Performer::getName).toArray()));

            _sm.notifySubscribers(SubscriptionType.CONCERT, newConcert, _uri.getBaseUri() + "concerts/" + newConcert.getId());
            _logger.info("Subscribers notified of new concert: " + newConcert.getTitle());

            return Response
                    .status(Response.Status.OK)
                    .location(new URI(_uri.getBaseUri() + "concerts/" + newConcert.getId()))
                    .build();
        } catch (URISyntaxException e) {
            _logger.info("Denied user agent: " + userAgent + "; could not convert return URI");
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        } finally {
            em.close();
        }
    }

    /**
     *  Subscribes a user to notifications related to ANY new concert added to the database
     * @param response
     * @param userAgent
     * @param authToken
     */
    @GET
    @Path("/getNotifications")
    @Consumes(MediaType.APPLICATION_XML)
    public void waitForNewConcerts(
            @Suspended AsyncResponse response,
            @HeaderParam("user-agent") String userAgent,
            @HeaderParam("Authorization") String authToken) {

        if (authToken == null) { // User has no access token
            _logger.info("Denied user agent: " + userAgent + "; No authentication token identified.");
            response.resume(Messages.UNAUTHENTICATED_REQUEST);
        }

        // Add AsyncResponse to subscribers for subscription type
        _sm.addSubscription(SubscriptionType.CONCERT, response);
        _logger.info("Subscriber added for new concerts");
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

    private User findUser(String authToken, EntityManager em) {

        TypedQuery<User> userQuery = em.createQuery("SELECT u FROM Token t JOIN t.user u WHERE t.token = :token", User.class);
        userQuery.setParameter("token", authToken);
        return userQuery.getSingleResult();
    }

    private String generateUserToken() {
        return UUID.randomUUID().toString();
    }
}