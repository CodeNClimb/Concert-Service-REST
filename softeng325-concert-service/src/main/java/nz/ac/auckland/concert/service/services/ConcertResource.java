package nz.ac.auckland.concert.service.services;

import nz.ac.auckland.concert.common.dto.*;
import nz.ac.auckland.concert.common.message.Messages;
import nz.ac.auckland.concert.service.domain.*;
import nz.ac.auckland.concert.service.domain.Mappers.*;
import nz.ac.auckland.concert.service.util.TheatreUtility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.persistence.*;
import javax.ws.rs.*;
import javax.ws.rs.core.GenericEntity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Path("/resources")
public class ConcertResource {

    private static final Logger _logger = LoggerFactory.getLogger(ConcertResource.class);

    private static final long AUTHENTICATION_TIMEOUT_MINUTES = 5; // 5 minutes
    private static final long RESERVATION_TIMEOUT_MILLIS = 1000; // 5 minutes

    private final PersistenceManager _pm;

    public ConcertResource() {

        _pm = PersistenceManager.instance();

    }

    // TODO: HATEOS or whatever

    @GET
    @Path("/concerts")
    @Produces(MediaType.APPLICATION_XML)
    public Response getConcerts(@HeaderParam("user-agent") String userAgent) {

        EntityManager em = _pm.createEntityManager();

        try {
            TypedQuery<Concert> q = em.createQuery("SELECT c FROM Concert c", Concert.class);
            List<Concert> concerts = q.getResultList();

            List<ConcertDTO> concertDTOs = concerts.stream().map(ConcertMapper::doDto).collect(Collectors.toList());
            GenericEntity<List<ConcertDTO>> entity = new GenericEntity<List<ConcertDTO>>(concertDTOs) {};
            _logger.info("Retrieved (" + concerts.size() + ") concerts; send to user agent: " + userAgent);

            return Response
                    .status(Response.Status.OK)
                    .entity(entity)
                    .build();
        } finally {
            em.close();
        }
    }

    @GET
    @Path("/performers")
    @Produces(MediaType.APPLICATION_XML)
    public Response getPerformers(@HeaderParam("user-agent") String userAgent) {

        EntityManager em = _pm.createEntityManager();

        try {
            TypedQuery<Performer> q = em.createQuery("SELECT p FROM Performer p", Performer.class);
            List<Performer> performers = q.getResultList();

            List<PerformerDTO> performerDTOs = performers.stream().map(PerformerMapper::toDto).collect(Collectors.toList());
            GenericEntity<List<PerformerDTO>> entity = new GenericEntity<List<PerformerDTO>>(performerDTOs) {};
            _logger.info("Retrieved (" + performers.size() + ") performers; send to user agent: " + userAgent);

            return Response
                    .status(Response.Status.OK)
                    .entity(entity)
                    .build();
        } finally {
            em.close();
        }
    }

    @GET
    @Path("/users/book")
    @Consumes(MediaType.APPLICATION_XML)
    @Produces(MediaType.APPLICATION_XML)
    public Response getBookings(@HeaderParam("user-agent") String userAgent, @HeaderParam("Authorization") String authToken) {

        if (authToken == null) { // User has no access token
            _logger.info("Denied user agent: " + userAgent + "; No authentication token identified.");
            return Response.status(Response.Status.FORBIDDEN).entity(Messages.UNAUTHENTICATED_REQUEST).build();
        }

        EntityManager em = _pm.createEntityManager();

        try {
            if (!tokenIsValid(authToken, em)) { // If token wasn't found or is expired return unauthorized
                _logger.info("Denied user agent : " + userAgent + "; With expired/invalid authentication token: " + authToken);
                return Response.status(Response.Status.UNAUTHORIZED).entity(Messages.BAD_AUTHENTICATON_TOKEN).build();
            }

            TypedQuery<Booking> bookingQuery = em.createQuery("SELECT b FROM Token t JOIN t.user u JOIN u.bookings b WHERE t.token = :token", Booking.class);
            bookingQuery.setParameter("token", authToken);
            List<Booking> bookings = bookingQuery.getResultList();

            Set<BookingDTO> bookingDTOS = bookings.stream().map(BookingMapper::toDto).collect(Collectors.toSet());
            GenericEntity<Set<BookingDTO>> entity = new GenericEntity<Set<BookingDTO>>(bookingDTOS) {};
            _logger.info("Retrieved (" + bookings.size() + ") bookings; Sent to user agent: " + userAgent);

            return Response
                    .status(Response.Status.OK)
                    .entity(entity)
                    .build();

        } finally {
            em.close();
        }
    }

    @POST
    @Path("/users")
    @Consumes(MediaType.APPLICATION_XML)
    @Produces(MediaType.APPLICATION_XML)
    public Response createUser(UserDTO userDto, @HeaderParam("user-agent") String userAgent) {

        if (userDto.getLastname() == null || userDto.getFirstname() == null || // If any necessary fields are not given
                userDto.getUsername() == null || userDto.getPassword() == null) {
            _logger.info("Denied user agent: " + userAgent + "; With missing field(s) in userDTO.");
            return Response.status(Response.Status.BAD_REQUEST).entity(Messages.CREATE_USER_WITH_MISSING_FIELDS).build(); // Bad request
        }

        EntityManager em = _pm.createEntityManager();

        try {
            EntityTransaction tx = em.getTransaction();
            tx.begin();

            User newUser = UserMapper.toDomain(userDto);
            em.persist(newUser);

            String token = generateUserToken();
            em.persist(new Token(newUser, token, LocalDateTime.now().plus(Duration.ofMinutes(AUTHENTICATION_TIMEOUT_MINUTES)))); // Create and persist auth. token for user

            tx.commit();
            tx.begin();

            User storedUser = em.find(User.class, userDto.getUsername());
            UserDTO returnDTO = UserMapper.toDTO(storedUser);
            _logger.info("Successfully created new user: [" + storedUser.getUsername() + ", " + storedUser.getFirstName() + ", " + storedUser.getLastName() + "]; Reply to user agent :" + userAgent);

            return Response
                    .status(Response.Status.OK)
                    .header("Authorization", token) // place auth token in header under Authorization:
                    .entity(returnDTO)
                    .build();
        } catch (RollbackException e) {
            _logger.info("Denied user agent: " + userAgent + "; Username [" + userDto.getUsername() + "] is already taken.");
            return Response.status(Response.Status.CONFLICT).entity(Messages.CREATE_USER_WITH_NON_UNIQUE_NAME).build();
        } finally {
            em.close();
        }
    }

    @POST
    @Path("/users/payment")
    @Consumes(MediaType.APPLICATION_XML)
    @Produces(MediaType.APPLICATION_XML)
    public Response createPayment(CreditCardDTO creditCard, @HeaderParam("user-agent") String userAgent, @HeaderParam("Authorization") String authToken) {

        if (authToken == null) { // User has no access token
            _logger.info("Denied user agent: " + userAgent + "; No authentication token identified.");
            return Response.status(Response.Status.FORBIDDEN).entity(Messages.UNAUTHENTICATED_REQUEST).build();
        }

        EntityManager em = _pm.createEntityManager();

        try {
            if (!tokenIsValid(authToken, em)) { // If token wasn't found or is expired return unauthorized
                _logger.info("Denied user agent : " + userAgent + "; With expired/invalid authentication token: " + authToken);
                return Response.status(Response.Status.UNAUTHORIZED).entity(Messages.BAD_AUTHENTICATON_TOKEN).build();
            }

            EntityTransaction tx = em.getTransaction();
            tx.begin();

            User foundUser = findUser(authToken, em);

            foundUser.setCreditCard(CreditCardMapper.toDomain(creditCard));
            em.merge(foundUser);
            tx.commit();
            _logger.info("Created new credit card for user: [" + foundUser.getUsername() + "]; Reply to user agent: " + userAgent);

            return Response
                    .status(Response.Status.NO_CONTENT)
                    .build();
        } finally {
            em.close();
        }
    }

    @POST
    @Path("/users/login")
    @Consumes(MediaType.APPLICATION_XML)
    @Produces(MediaType.APPLICATION_XML)
    public Response authenticateUser(UserDTO userDTO, @HeaderParam("user-agent") String userAgent) {

        if (userDTO.getUsername() == null || userDTO.getPassword() == null) { // If either username or password is empty
            _logger.info("Denied user agent: " + userAgent + "; With missing field(s) in userDTO.");
            return Response.status(Response.Status.BAD_REQUEST).entity(Messages.AUTHENTICATE_USER_WITH_MISSING_FIELDS).build(); // Bad request
        }

        EntityManager em = _pm.createEntityManager();

        try {
            // Check login details are correct
            User foundUser = em.find(User.class, userDTO.getUsername());
            if (foundUser == null) {// No user found
                _logger.info("Denied user agent: " + userAgent + "; No user was found with username: " + userDTO.getUsername());
                return Response.status(Response.Status.NOT_FOUND).entity(Messages.AUTHENTICATE_NON_EXISTENT_USER).build();
            } else if (!foundUser.getPassword().equals(userDTO.getPassword())) { // Login credentials incorrect
                _logger.info("Denied user agent: " + userAgent + "; Login credentials for [" + foundUser.getUsername() + "] incorrect.");
                return Response.status(Response.Status.UNAUTHORIZED).entity(Messages.AUTHENTICATE_USER_WITH_ILLEGAL_PASSWORD).build();
            }

            // Login is correct, so we return token either newly generated or already one stored if still active.
            Token token = em.find(Token.class, foundUser.getUsername()); // One token for one user

            // either token does not exist for this user or has timed out - if has timed out service should provide a new one
            String tokenString;
            if (token == null || (token.getExpiry().isBefore(LocalDateTime.now()))) {

                if (token != null) // Remove the current token if one exists
                    em.remove(token);

                tokenString = generateUserToken();

                // Place new token into db
                EntityTransaction tx = em.getTransaction();
                tx.begin();

                Token tokenToPlace = new Token(foundUser, tokenString, LocalDateTime.now().plus(Duration.ofMinutes(AUTHENTICATION_TIMEOUT_MINUTES)));
                em.persist(tokenToPlace);

                tx.commit();
                _logger.info("Created new token [" + tokenToPlace + "]; For user: " + foundUser.getUsername());
            } else { // Token stored in db both exists and is still valid
                tokenString = token.getToken(); // Add existing token to response
                _logger.info("Retrieved existing valid token [" + tokenString + "]; For user: " + foundUser.getUsername());
            }
            _logger.info("Send token [" + tokenString + "] to user agent: " + userAgent);

            return Response
                    .status(Response.Status.OK)
                    .header("Authorization", tokenString) // place auth token in header under Authorization:
                    .entity(UserMapper.toDTO(foundUser))
                    .build();
        } finally {
            em.close();
        }
    }

    @POST
    @Path("/reserve")
    @Consumes(MediaType.APPLICATION_XML)
    @Produces(MediaType.APPLICATION_XML)
    public Response reserveSeats(ReservationRequestDTO requestDto, @HeaderParam("user-agent") String userAgent, @HeaderParam("Authorization") String authToken) {

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
            tx.begin();

            Set<SeatDTO> unavailableSeats;

            // Get all existing bookings and current reservations for this concert on this date
            TypedQuery<SeatReservation> unavailableSeatsBookingQuery = em.createQuery("SELECT s FROM Booking b JOIN b.reservation r JOIN r.concert c JOIN r.seats s WHERE c.id = :concertId AND r.date = :date", SeatReservation.class);
            unavailableSeatsBookingQuery.setParameter("concertId", requestDto.getConcertId());
            unavailableSeatsBookingQuery.setParameter("date",  requestDto.getDate());
            List<SeatReservation> seatsBooked = unavailableSeatsBookingQuery.getResultList();

            // Add all seats found in returned bookings to current unavailable seats
            unavailableSeats = seatsBooked.stream().map(SeatMapper::toDto).collect(Collectors.toSet());

            // Find all seats currently reserved by another reservation process - check that timeout has not been reached using current time
            TypedQuery<SeatReservation> unavailableSeatsReservationQuery = em.createQuery("SELECT s FROM Reservation r JOIN r.concert c JOIN r.seats s WHERE c.id = :concertId AND r.date = :date AND r.expiry > :currentTime", SeatReservation.class);
            unavailableSeatsReservationQuery.setParameter("concertId", requestDto.getConcertId());
            unavailableSeatsReservationQuery.setParameter("date", requestDto.getDate());
            unavailableSeatsReservationQuery.setParameter("currentTime", LocalDateTime.now());
            List<SeatReservation> seatsCurrentlyReserved = unavailableSeatsReservationQuery.getResultList();

            // Add all seats found in returned reservations to current unavailable seats
            // If a seat is both booked AND still under valid reservation it is only added to unavailableSeats ONCE as it is a Set<>
            seatsCurrentlyReserved.stream().map(SeatMapper::toDto).forEach(unavailableSeats::add);
            _logger.info("There are currently (" + unavailableSeats.size() + ") unavailable seats for concert id: " + requestDto.getConcertId() + " on date: " + requestDto.getDate());

            // Acquire reserved seats
            Set<SeatDTO> reservedSeats = TheatreUtility.findAvailableSeats(requestDto.getNumberOfSeats(), requestDto.getSeatType(), unavailableSeats);
            if (reservedSeats.isEmpty()) { // Not enough seats left to reserve
                _logger.info("Denied user agent: " + userAgent + "; Requested (" + requestDto.getNumberOfSeats() + ") seats; Not enough available seats for concert id: " + requestDto.getConcertId() + " on date: " + requestDto.getDate());
                return Response.status(Response.Status.CONFLICT).entity(Messages.INSUFFICIENT_SEATS_AVAILABLE_FOR_RESERVATION).build();
            }

            // Create new reservation and persist to database
            Reservation newReservation = new Reservation(
                    reservedSeats.stream().map(SeatMapper::toReservation).collect(Collectors.toSet()), // Reserved seats
                    em.find(Concert.class, requestDto.getConcertId()), // Corresponding concert from db
                    requestDto.getDate(), // Given date
                    LocalDateTime.now().plus(Duration.ofMillis(RESERVATION_TIMEOUT_MILLIS)), // now plus given reservation timeout
                    requestDto.getSeatType()
            );
            User user = findUser(authToken, em);
            user.setReservation(newReservation);
            User mergedUser = em.merge(user);
            em.flush(); // generate id for newReservation
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

    @POST
    @Path("/reserve/book")
    @Consumes(MediaType.APPLICATION_XML)
    @Produces(MediaType.APPLICATION_XML)
    public Response bookSeats(ReservationDTO reservationDto, @HeaderParam("user-agent") String userAgent, @HeaderParam("Authorization") String authToken) {

        if (authToken == null) {
            _logger.info("Denied user agent: " + userAgent + "; No authentication token identified.");
            return Response.status(Response.Status.FORBIDDEN).entity(Messages.UNAUTHENTICATED_REQUEST).build();
        }

        EntityManager em = _pm.createEntityManager();

        try {
            if (!tokenIsValid(authToken, em)) {
                _logger.info("Denied user agent : " + userAgent + "; With expired/invalid authentication token: " + authToken);
                return Response.status(Response.Status.UNAUTHORIZED).entity(Messages.BAD_AUTHENTICATON_TOKEN).build();
            }

            EntityTransaction tx = em.getTransaction();
            tx.begin();

            TypedQuery<CreditCard> creditCardQuery = em.createQuery("SELECT c FROM Token t JOIN t.user u JOIN u.creditCard c WHERE t.token = :token", CreditCard.class);
            creditCardQuery.setParameter("token", authToken);
            try {
                CreditCard creditCard = creditCardQuery.getSingleResult();
            } catch (NoResultException e) {
                _logger.info("Denied user agent: " + userAgent + "; No credit card found under account.");
                return Response.status(Response.Status.PAYMENT_REQUIRED).entity(Messages.CREDIT_CARD_NOT_REGISTERED).build();
            }

            TypedQuery<Reservation> reservationQuery = em.createQuery("SELECT r FROM Token t JOIN t.user u JOIN u.reservation r WHERE t.token = :token", Reservation.class);
            reservationQuery.setParameter("token", authToken);
            Reservation foundReservation = reservationQuery.getSingleResult();

            if (!LocalDateTime.now().isBefore(foundReservation.getExpiry())) {
                _logger.info("Denied user agent: " + userAgent + "; reservation for concert id: " +
                        reservationDto.getReservationRequest().getConcertId() + " on date: " + reservationDto.getReservationRequest().getDate() +
                        " timed out at: " + foundReservation.getExpiry());
                return Response.status(Response.Status.REQUEST_TIMEOUT).entity(Messages.EXPIRED_RESERVATION).build();
            }

            Booking newBooking = new Booking(foundReservation, findUser(authToken, em));
            em.persist(newBooking);
            tx.commit();
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