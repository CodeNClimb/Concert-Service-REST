package nz.ac.auckland.concert.service.services;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.AmazonS3Exception;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.util.IOUtils;
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
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDateTime;

@Path("/images")
public class ImageResource {

    // AWS S3 access credentials for concert images.
    protected static final String AWS_ACCESS_KEY_ID = Config.AWS_ACCESS_KEY_ID;
    protected static final String AWS_SECRET_ACCESS_KEY = Config.AWS_SECRET_ACCESS_KEY;

    // Name of the S3 bucket that stores images.
    protected static final String AWS_BUCKET = Config.AWS_BUCKET;

    private static final Logger _logger = LoggerFactory.getLogger(ImageResource.class);

    @Context // Information about the service hosted URI
    private static UriInfo _uri;

    private final PersistenceManager _pm; // Persistence
    private final SubscriptionManager _sm; // Subscription management

    public ImageResource() {

        _pm = PersistenceManager.instance();
        _sm = SubscriptionManager.instance();
    }

    @GET
    @Produces("image/png")
    @Path("/{imageName}")
    public Response getImage(
            @HeaderParam("user-agent") String userAgent,
            @PathParam("imageName") String imageName) {

        try {
            BasicAWSCredentials awsCredentials = new BasicAWSCredentials(AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY);
            AmazonS3 s3 = AmazonS3ClientBuilder
                    .standard()
                    .withRegion(Regions.AP_SOUTHEAST_2)
                    .withCredentials(new AWSStaticCredentialsProvider(awsCredentials))
                    .build();

            S3Object object = s3.getObject(AWS_BUCKET, imageName);
            byte[] byteArray = IOUtils.toByteArray(object.getObjectContent());

            _logger.info("Successfully downloaded " + imageName + " from AWS.");

            return Response
                    .status(Response.Status.OK)
                    .entity((StreamingOutput) outputStream -> {
                        outputStream.write(byteArray);
                        outputStream.flush();
                    })
                    .build();
        } catch (AmazonS3Exception e) {
            return Response.status(Response.Status.NOT_FOUND).entity(Messages.NO_IMAGE_FOR_PERFORMER).build();
        } catch (IOException e) {
            return Response.status(Response.Status.SERVICE_UNAVAILABLE).entity(Messages.SERVICE_COMMUNICATION_ERROR).build();
        }
    }

    /**
     * Creates a new image entry under a performer. Authentication is required and can be
     * provided through an authorization token.
     * @param performerDTO
     * @param userAgent
     * @param authToken
     * @return URI to requested image
     */
    @PUT
    @Path("/")
    public Response addImage(
            PerformerDTO performerDTO,
            @HeaderParam("user-agent") String userAgent,
            @HeaderParam("Authorization") String authToken) {
        if (authToken == null) { // User has no access token
            _logger.info("Denied user agent: " + userAgent + "; No authentication token identified.");
            return Response.status(Response.Status.FORBIDDEN).entity(Messages.UNAUTHENTICATED_REQUEST).build();
        }

        if (performerDTO.getImageName() == null || performerDTO.getId() == null) { // Any necessary fields are missing
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

            Performer performer = em.find(Performer.class, performerDTO.getId());
            performer.setImageName(performerDTO.getImageName());

            tx.commit();

            _logger.info("Successfully added image " + performer.getImageName() + " to performer " + performer.getName() + " with id (" + performer.getId() + ")");
            PerformerDTO returnPerformerDto = PerformerMapper.toDto(performer);

            _sm.notifySubscribers(SubscriptionType.PERFORMER_IMAGE, performer, _uri.getBaseUri() + "images/" + performer.getImageName());
            _sm.notifySubscribersWithId(SubscriptionType.PERFORMER_IMAGE, performer, performer.getId(), _uri.getBaseUri() + "images/" + performer.getImageName());
            _logger.info("Subscribers notified of new image " + performer.getImageName() + " for performer " + performer.getName());

            return Response
                    .status(Response.Status.OK)
                    .location(new URI(_uri.getBaseUri() + "images/" + performer.getImageName()))
                    .entity(returnPerformerDto)
                    .build();
        } catch (URISyntaxException e) {
            _logger.info("Denied user agent: " + userAgent + "; could not convert return URI");
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        } finally {
            em.close();
        }
    }

    /**
     *  Subscribes a user to notifications related to ANY new image added to the database
     * @param response
     * @param userAgent
     * @param authToken
     */
    @GET
    @Path("/getNotifications")
    @Consumes(MediaType.APPLICATION_XML)
    public void waitForNewImages(
            @Suspended AsyncResponse response,
            @HeaderParam("user-agent") String userAgent,
            @HeaderParam("Authorization") String authToken,
            @CookieParam("latest-news") String newsCookie) {

        if (authToken == null) { // User has no access token
            _logger.info("Denied user agent: " + userAgent + "; No authentication token identified.");
            response.resume(Messages.UNAUTHENTICATED_REQUEST);
        }

        // Add AsyncResponse to subscribers for subscription type
        _sm.addSubscription(SubscriptionType.PERFORMER_IMAGE, response, newsCookie);
        _logger.info("Subscriber added for new images");
    }

    /**
     * Subscribes a user to notifications related to new images added to the database related to a particular artist
     * @param response
     * @param userAgent
     * @param authToken
     * @param performerId
     */
    @GET
    @Path("/getNotifications/{id}")
    @Consumes(MediaType.APPLICATION_XML)
    public void waitForNewImagesForPerformer(
            @Suspended AsyncResponse response,
            @HeaderParam("user-agent") String userAgent,
            @HeaderParam("Authorization") String authToken,
            @PathParam("id") String performerId,
            @CookieParam("latest-news") String newsCookie) {

        if (authToken == null) { // User has no access token
            _logger.info("Denied user agent: " + userAgent + "; No authentication token identified.");
            response.resume(Messages.UNAUTHENTICATED_REQUEST);
        }

        // Add AsyncResponse to subscribers for subscription type
        _sm.addSubscriptionWithId(SubscriptionType.PERFORMER_IMAGE, response, Long.decode(performerId), newsCookie);
        _logger.info("Subscriber added for new images for performer with id (" + performerId + ")");
    }


    // Private Methods


    private boolean tokenIsValid(String authToken, EntityManager em) {

        // Retrieve corresponding token form the database
        TypedQuery<Token> tokenQuery = em.createQuery("SELECT t FROM Token t WHERE t.token = :token", Token.class);
        tokenQuery.setParameter("token", authToken);
        Token token = tokenQuery.getSingleResult();

        // True if token isn't null and its expiry time is after the current time
        return token != null && !LocalDateTime.now().isAfter(token.getExpiry());
    }
}
