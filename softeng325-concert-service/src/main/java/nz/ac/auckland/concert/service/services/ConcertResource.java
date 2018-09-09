package nz.ac.auckland.concert.service.services;

import com.sun.scenario.effect.impl.sw.sse.SSEBlend_SRC_OUTPeer;
import nz.ac.auckland.concert.common.dto.*;
import nz.ac.auckland.concert.common.message.Messages;
import nz.ac.auckland.concert.service.domain.*;
import nz.ac.auckland.concert.service.domain.Mappers.*;
import nz.ac.auckland.concert.service.domain.Types.SubscriptionType;
import nz.ac.auckland.concert.service.util.TheatreUtility;
import org.hibernate.tool.schema.internal.exec.ScriptTargetOutputToFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.persistence.*;
import javax.ws.rs.*;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Path("/resources")
public class ConcertResource {

    private static final Logger _logger = LoggerFactory.getLogger(ConcertResource.class);

    private static final long AUTHENTICATION_TIMEOUT_MINUTES = 5; // 5 minutes
    private static final long RESERVATION_TIMEOUT_MILLIS = 1000; // 1 second

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
    @Path("/concerts/{id}")
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
    @Path("/concerts")
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
                    .location(new URI(_uri.getBaseUri() + String.format("resources/concerts?start=%d&size=%d", start + size, size))) // next batch of concerts
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
     * This method creates a temporary reservation for a user for a set of seats on a particular concert. When called,
     * the service searches for available seats and (if enough are available) it will return the reservation object and
     * keep the reservation active to a short period of time. it id up to the client to confirm the reservation
     * within the allocated time else the booking will fail.
     * @param requestDto
     * @param userAgent
     * @param authToken
     * @return The reservation object made by the user.
     */
    @POST
    @Path("/reserve")
    @Consumes(MediaType.APPLICATION_XML)
    @Produces(MediaType.APPLICATION_XML)
    public Response reserveSeats(
            ReservationRequestDTO requestDto,
            @HeaderParam("user-agent") String userAgent,
            @HeaderParam("Authorization") String authToken) {

        if (authToken == null) { // User has no access token
            _logger.info("Denied user agent: " + userAgent + "; No authentication token identified.");
            return Response.status(Response.Status.FORBIDDEN).entity(Messages.UNAUTHENTICATED_REQUEST).build();
        }

        if (requestDto.getConcertId() == null || requestDto.getDate() == null ||
                requestDto.getNumberOfSeats() == 0 || requestDto.getSeatType() == null) { // Any necessary fields are missing
            _logger.info("Denied user agent: " + userAgent + "; With missing field(s) in reservationRequestDTO.");
            return Response.status(Response.Status.BAD_REQUEST).entity(Messages.RESERVATION_REQUEST_WITH_MISSING_FIELDS).build(); // Bad request
        }

        EntityManager em = _pm.createEntityManager();

        try {
            if (!tokenIsValid(authToken, em)) { // If token wasn't found or is expired return unauthorized
                _logger.info("Denied user agent : " + userAgent + "; With expired/invalid authentication token: " + authToken);
                return Response.status(Response.Status.UNAUTHORIZED).entity(Messages.BAD_AUTHENTICATON_TOKEN).build();
            }

            // Check that the concert in question has a corresponding date in the db.
            TypedQuery<LocalDateTime> concertDateQuery = em.createQuery("SELECT d FROM Concert c JOIN c.dates d WHERE c.id = :id", LocalDateTime.class);
            concertDateQuery.setParameter("id", requestDto.getConcertId());
            List<LocalDateTime> dates = concertDateQuery.getResultList();
            if (!dates.contains(requestDto.getDate())) { // No concert was found on this date
                _logger.info("Not concert(s) with id: " + requestDto.getConcertId() + " found on date: " + requestDto.getDate());
                return Response.status(Response.Status.NOT_FOUND).entity(Messages.CONCERT_NOT_SCHEDULED_ON_RESERVATION_DATE).build();
            }

            EntityTransaction tx = em.getTransaction();
            tx.begin(); // Ensure entire reading of seats unavailable AND reservation stages are atomic - ensures no conflicting requests from differing clients

            Set<SeatDTO> unavailableSeats;

            // Get all existing bookings and current reservations for this concert on this date
            TypedQuery<SeatReservation> unavailableSeatsBookingQuery = em.createQuery(
                    "SELECT s FROM Booking b JOIN b.reservation r JOIN r.concert c JOIN r.seats s WHERE c.id = :concertId AND r.date = :date", SeatReservation.class);
            unavailableSeatsBookingQuery.setParameter("concertId", requestDto.getConcertId());
            unavailableSeatsBookingQuery.setParameter("date",  requestDto.getDate());
            List<SeatReservation> seatsBooked = unavailableSeatsBookingQuery.getResultList();

            // Add all seats found in returned bookings to current unavailable seats
            unavailableSeats = seatsBooked.stream().map(SeatMapper::toDto).collect(Collectors.toSet());

            // Find all seats currently reserved by another reservation process - check that timeout has not been reached using current time
            TypedQuery<SeatReservation> unavailableSeatsReservationQuery = em.createQuery( // Note: this query checks for expiry time so only ACTIVE reservations are returned
                    "SELECT s FROM Reservation r JOIN r.concert c JOIN r.seats s WHERE c.id = :concertId AND r.date = :date AND r.expiry > :currentTime", SeatReservation.class);
            unavailableSeatsReservationQuery.setParameter("concertId", requestDto.getConcertId());
            unavailableSeatsReservationQuery.setParameter("date", requestDto.getDate());
            unavailableSeatsReservationQuery.setParameter("currentTime", LocalDateTime.now());
            List<SeatReservation> seatsCurrentlyReserved = unavailableSeatsReservationQuery.getResultList();

            // Add all seats found in returned active reservations to current unavailable seats
            // If a seat is both booked AND still under valid reservation it is only added to unavailableSeats ONCE as it is a Set<> which does not allow duplicates
            seatsCurrentlyReserved.stream().map(SeatMapper::toDto).forEach(unavailableSeats::add);
            _logger.info("There are currently (" + unavailableSeats.size() + ") unavailable seats for concert id: " + requestDto.getConcertId() + " on date: " + requestDto.getDate());

            // Acquire reserved seats w.r.t. unavailable seats
            Set<SeatDTO> reservedSeats = TheatreUtility.findAvailableSeats(requestDto.getNumberOfSeats(), requestDto.getSeatType(), unavailableSeats);
            if (reservedSeats.isEmpty()) { // Not enough seats left to reserve
                _logger.info("Denied user agent: " + userAgent + "; Requested (" + requestDto.getNumberOfSeats() + ") seats; Not enough available seats for concert id: " + requestDto.getConcertId() + " on date: " + requestDto.getDate());
                return Response.status(Response.Status.CONFLICT).entity(Messages.INSUFFICIENT_SEATS_AVAILABLE_FOR_RESERVATION).build();
            }

            // Create new reservation and persist to database
            Reservation newReservation = new Reservation(
                    reservedSeats.stream().map(SeatMapper::toReservation).collect(Collectors.toSet()), // Client's reserved seats
                    em.find(Concert.class, requestDto.getConcertId()), // Corresponding concert from db
                    requestDto.getDate(), // Given date
                    LocalDateTime.now().plus(Duration.ofMillis(RESERVATION_TIMEOUT_MILLIS)), // now plus given reservation timeout
                    requestDto.getSeatType()
            );
            User user = findUser(authToken, em);
            user.setReservation(newReservation);
            User mergedUser = em.merge(user);
            tx.commit();

            ReservationDTO returnReservation = new ReservationDTO(
                    mergedUser.getReservation().getId(),
                    requestDto,
                    reservedSeats
            );
            _logger.info("Created new reservation for (" + newReservation.getSeats().size() + ") seats for concert id: " + newReservation.getConcert().getId() + " on date: " + newReservation.getDate() + "; For user: " + user.getUsername());
            _logger.info("Reply to user agent :" + userAgent);

            return Response
                    .status(Response.Status.OK)
                    .entity(returnReservation)
                    .build();
        } finally {
            em.close();
        }
    }

    /**
     * This method allows a user to confirm a reservation and create a booking entity for the service. If a client
     * calls this method before the reservation timeout, (while sending a valid reservation object) a new booking will
     * be stored in the service's database for that user and concert.
     * @param reservationDto
     * @param userAgent
     * @param authToken
     * @return Status code
     */
    @POST
    @Path("/reserve/book")
    @Consumes(MediaType.APPLICATION_XML)
    @Produces(MediaType.APPLICATION_XML)
    public Response bookSeats(
            ReservationDTO reservationDto,
            @HeaderParam("user-agent") String userAgent,
            @HeaderParam("Authorization") String authToken) {

        if (authToken == null) { // no authorization token present
            _logger.info("Denied user agent: " + userAgent + "; No authentication token identified.");
            return Response.status(Response.Status.FORBIDDEN).entity(Messages.UNAUTHENTICATED_REQUEST).build();
        }

        EntityManager em = _pm.createEntityManager();

        try {
            if (!tokenIsValid(authToken, em)) { // Authentication token has expired
                _logger.info("Denied user agent : " + userAgent + "; With expired/invalid authentication token: " + authToken);
                return Response.status(Response.Status.UNAUTHORIZED).entity(Messages.BAD_AUTHENTICATON_TOKEN).build();
            }

            TypedQuery<CreditCard> creditCardQuery = em.createQuery("SELECT c FROM Token t JOIN t.user u JOIN u.creditCard c WHERE t.token = :token", CreditCard.class);
            creditCardQuery.setParameter("token", authToken);
            try {
                CreditCard creditCard = creditCardQuery.getSingleResult();
            } catch (NoResultException e) { // User doesn't have any credit card associated with their account
                _logger.info("Denied user agent: " + userAgent + "; No credit card found under account.");
                return Response.status(Response.Status.PAYMENT_REQUIRED).entity(Messages.CREDIT_CARD_NOT_REGISTERED).build();
            }

            EntityTransaction tx = em.getTransaction();
            tx.begin(); // make the operation of checking reservation expiry to making booking atomic

            TypedQuery<Reservation> reservationQuery = em.createQuery("SELECT r FROM Token t JOIN t.user u JOIN u.reservation r WHERE t.token = :token", Reservation.class);
            reservationQuery.setParameter("token", authToken);
            Reservation foundReservation = reservationQuery.getSingleResult(); // Get reservation for that user (obviously only one allowed at any one time)

            // Check if reservation has expired
            if (!LocalDateTime.now().isBefore(foundReservation.getExpiry())) {
                _logger.info("Denied user agent: " + userAgent + "; reservation for concert id: " +
                        reservationDto.getReservationRequest().getConcertId() + " on date: " + reservationDto.getReservationRequest().getDate() +
                        " timed out at: " + foundReservation.getExpiry());
                return Response.status(Response.Status.REQUEST_TIMEOUT).entity(Messages.EXPIRED_RESERVATION).build();
            }

            Booking newBooking = new Booking(foundReservation, findUser(authToken, em));
            em.persist(newBooking);
            tx.commit(); // End of atomic operation
            _logger.info("Created booking for concert id: " +
                    reservationDto.getReservationRequest().getConcertId() + " on date: " + reservationDto.getReservationRequest().getDate() +
                    "; Reply to user agent: " + userAgent);

            return Response
                    .status(Response.Status.NO_CONTENT)
                    .build();
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
    @Path("/concerts")
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

            _sm.notifySubscribers(SubscriptionType.CONCERT, newConcert, _uri.getBaseUri() + "resources/concerts/" + newConcert.getId());
            _logger.info("Subscribers notified of new concert: " + newConcert.getTitle());

            return Response
                    .status(Response.Status.OK)
                    .location(new URI(_uri.getBaseUri() + "resources/concerts/" + newConcert.getId()))
                    .build();
        } catch (URISyntaxException e) {
            _logger.info("Denied user agent: " + userAgent + "; could not convert return URI");
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        } finally {
            em.close();
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
    @Path("/images")
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

            _sm.notifySubscribers(SubscriptionType.PERFORMER_IMAGE, performer, _uri.getBaseUri() + "resources/images/" + performer.getImageName());
            _sm.notifySubscribersWithId(SubscriptionType.PERFORMER_IMAGE, performer, performer.getId(), _uri.getBaseUri() + "resources/images/" + performer.getImageName());
            _logger.info("Subscribers notified of new image " + performer.getImageName() + " for performer " + performer.getName());

            return Response
                    .status(Response.Status.OK)
                    .location(new URI(_uri.getBaseUri() + "resources/images/" + performer.getImageName()))
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
    @Path("/images/getNotifications")
    @Consumes(MediaType.APPLICATION_XML)
    public void waitForNewImages(
            @Suspended AsyncResponse response,
            @HeaderParam("user-agent") String userAgent,
            @HeaderParam("Authorization") String authToken) {

        if (authToken == null) { // User has no access token
            _logger.info("Denied user agent: " + userAgent + "; No authentication token identified.");
            response.resume(Messages.UNAUTHENTICATED_REQUEST);
        }

        // Add AsyncResponse to subscribers for subscription type
        _sm.addSubscription(SubscriptionType.PERFORMER_IMAGE, response);
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
    @Path("/images/getNotifications/{id}")
    @Consumes(MediaType.APPLICATION_XML)
    public void waitForNewImagesForPerformer(
            @Suspended AsyncResponse response,
            @HeaderParam("user-agent") String userAgent,
            @HeaderParam("Authorization") String authToken,
            @PathParam("id") String performerId) {

        if (authToken == null) { // User has no access token
            _logger.info("Denied user agent: " + userAgent + "; No authentication token identified.");
            response.resume(Messages.UNAUTHENTICATED_REQUEST);
        }

        // Add AsyncResponse to subscribers for subscription type
        _sm.addSubscriptionWithId(SubscriptionType.PERFORMER_IMAGE, response, Long.decode(performerId));
        _logger.info("Subscriber added for new images for performer with id (" + performerId + ")");
    }

    /**
     *  Subscribes a user to notifications related to ANY new concert added to the database
     * @param response
     * @param userAgent
     * @param authToken
     */
    @GET
    @Path("/concerts/getNotifications")
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