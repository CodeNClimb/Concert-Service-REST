package nz.ac.auckland.concert.service.services;

import nz.ac.auckland.concert.common.dto.ReservationDTO;
import nz.ac.auckland.concert.common.dto.ReservationRequestDTO;
import nz.ac.auckland.concert.common.dto.SeatDTO;
import nz.ac.auckland.concert.common.message.Messages;
import nz.ac.auckland.concert.service.domain.*;
import nz.ac.auckland.concert.service.domain.Mappers.SeatMapper;
import nz.ac.auckland.concert.service.util.TheatreUtility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.persistence.*;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Path("/reserve")
public class ReserveResource {

    private static final Logger _logger = LoggerFactory.getLogger(ReserveResource.class);

    private static final long RESERVATION_TIMEOUT_MILLIS = 1000; // 1 second

    @Context // Information about the service hosted URI
    private static UriInfo _uri;

    private final PersistenceManager _pm; // Persistence

    public ReserveResource() {

        _pm = PersistenceManager.instance();
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
    @Path("/")
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
            // Check for a change in version upon commit, fine to have only optimistic as not writing to it
            unavailableSeatsBookingQuery.setLockMode(LockModeType.OPTIMISTIC);
            List<SeatReservation> seatsBooked = unavailableSeatsBookingQuery.getResultList();

            // Add all seats found in returned bookings to current unavailable seats
            unavailableSeats = seatsBooked.stream().map(SeatMapper::toDto).collect(Collectors.toSet());

            // Find all seats currently reserved by another reservation process - check that timeout has not been reached using current time
            TypedQuery<SeatReservation> unavailableSeatsReservationQuery = em.createQuery( // Note: this query checks for expiry time so only ACTIVE reservations are returned
                    "SELECT s FROM Reservation r JOIN r.concert c JOIN r.seats s WHERE c.id = :concertId AND r.date = :date AND r.expiry > :currentTime", SeatReservation.class);
            unavailableSeatsReservationQuery.setParameter("concertId", requestDto.getConcertId());
            unavailableSeatsReservationQuery.setParameter("date", requestDto.getDate());
            unavailableSeatsReservationQuery.setParameter("currentTime", LocalDateTime.now());
            // Ensure version is incremented after this read, need this level because this table is written to at the end of this tx.
            unavailableSeatsReservationQuery.setLockMode(LockModeType.OPTIMISTIC_FORCE_INCREMENT);
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
    @Path("/book")
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
            // Optimistic force increment to ensure no other user reserves these seats after they time out and before this book is committed.
            reservationQuery.setLockMode(LockModeType.OPTIMISTIC_FORCE_INCREMENT);
            Reservation foundReservation = reservationQuery.getSingleResult(); // Get reservation for that user (obviously only one allowed at any one time)

            // Check if reservation has expired
            if (!LocalDateTime.now().isBefore(foundReservation.getExpiry())) {
                _logger.info("Denied user agent: " + userAgent + "; reservation for concert id: " +
                        reservationDto.getReservationRequest().getConcertId() + " on date: " + reservationDto.getReservationRequest().getDate() +
                        " timed out at: " + foundReservation.getExpiry());
                return Response.status(Response.Status.REQUEST_TIMEOUT).entity(Messages.EXPIRED_RESERVATION).build();
            }

            Booking newBooking = new Booking(foundReservation, findUser(authToken, em));
            em.persist(newBooking); // This ensures increment of version number for booking table
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
}
