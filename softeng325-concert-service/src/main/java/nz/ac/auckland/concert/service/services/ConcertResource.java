package nz.ac.auckland.concert.service.services;

import nz.ac.auckland.concert.common.dto.*;
import nz.ac.auckland.concert.common.message.Messages;
import nz.ac.auckland.concert.service.domain.*;
import nz.ac.auckland.concert.service.domain.Mappers.*;
import nz.ac.auckland.concert.service.domain.Types.SubscriptionType;
import nz.ac.auckland.concert.service.util.TheatreUtility;
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
    private static final long RESERVATION_TIMEOUT_MILLIS = 1000; // 5 minutes

    @Context
    private static UriInfo _uri;

    private final PersistenceManager _pm;
    private final SubscriptionManager _sm;

    public ConcertResource() {
        _pm = PersistenceManager.instance();
        _sm = SubscriptionManager.instance();
    }

    // TODO: concurrency

    @GET
    @Path("/concerts/{id}")
    @Produces(MediaType.APPLICATION_XML)
    public Response getConcert(
            @HeaderParam("user-agent") String userAgent,
            @PathParam("id") long id) {

        EntityManager em = _pm.createEntityManager();

        Concert concert = em.find(Concert.class, id);
        ConcertDTO returnConcert = ConcertMapper.toDto(concert);

        return Response
                .status(Response.Status.OK)
                .entity(returnConcert)
                .build();
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
                    .location(new URI(_uri.getBaseUri() + String.format("resources/concerts?start=%d&size=%d", start + size, size)))
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
     * This method allows for multiple performers to be retrieved in batches up to the clients discretion.
     * No authentication is required here.
     * @param userAgent
     * @param start
     * @param size
     * @return list of performers with uri for next batch
     */
    @GET
    @Path("/performers")
    @Produces(MediaType.APPLICATION_XML)
    public Response getPerformers(
            @HeaderParam("user-agent") String userAgent,
            @QueryParam("start") int start,
            @QueryParam("size") int size) {

        EntityManager em = _pm.createEntityManager();

        try {
            TypedQuery<Performer> q = em.createQuery("SELECT p FROM Performer p", Performer.class);
            List<Performer> performers = q.setFirstResult(start).setMaxResults(size).getResultList();

            List<PerformerDTO> performerDTOs = performers.stream().map(PerformerMapper::toDto).collect(Collectors.toList());
            GenericEntity<List<PerformerDTO>> entity = new GenericEntity<List<PerformerDTO>>(performerDTOs) {};
            _logger.info("Retrieved (" + performers.size() + ") performers; send to user agent: " + userAgent);

            return Response
                    .status(Response.Status.OK)
                    .location(new URI(_uri.getBaseUri() + String.format("resources/performers?start=%d&size=%d", start + size, size)))
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
     * This method allows for multiple bookings to be retrieved in batches up to the clients discretion.
     * Authentication is require and can be provided with an authorization token.
     * @param userAgent
     * @param authToken
     * @param start
     * @param size
     * @return list of bookings with uri for next batch
     */
    @GET
    @Path("/users/book")
    @Consumes(MediaType.APPLICATION_XML)
    @Produces(MediaType.APPLICATION_XML)
    public Response getBookings(
            @HeaderParam("user-agent") String userAgent,
            @HeaderParam("Authorization") String authToken,
            @QueryParam("start") int start,
            @QueryParam("size") int size) {

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
            List<Booking> bookings = bookingQuery.setFirstResult(start).setMaxResults(size).getResultList();

            Set<BookingDTO> bookingDTOS = bookings.stream().map(BookingMapper::toDto).collect(Collectors.toSet());
            GenericEntity<Set<BookingDTO>> entity = new GenericEntity<Set<BookingDTO>>(bookingDTOS) {};
            _logger.info("Retrieved (" + bookings.size() + ") bookings; Sent to user agent: " + userAgent);

            return Response
                    .status(Response.Status.OK)
                    .location(new URI(_uri.getBaseUri() + String.format("resources/users/book?start=%d&size=%d", start + size, size)))
                    .entity(entity)
                    .build();

        } catch (URISyntaxException e) {
            _logger.info("Denied user agent: " + userAgent + "; could not convert return URI");
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        } finally {
            em.close();
        }
    }

    // TODO: make this hateos
    /**
     * This method creates and stores a new user entity in the database of the service, given a userDTO object sent
     * from the client. The new user must have a unique username, and the user is returned an authorization token
     * when successful account creation occurs.
     * @param userDto
     * @param userAgent
     * @return URI to created user
     */
    @POST
    @Path("/users")
    @Consumes(MediaType.APPLICATION_XML)
    @Produces(MediaType.APPLICATION_XML)
    public Response createUser(
            UserDTO userDto,
            @HeaderParam("user-agent") String userAgent) {

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
                    .location(new URI(_uri.getBaseUri() + "resources/users/" + returnDTO.getUsername())) // Return location of new user
                    .entity(returnDTO)
                    .build();
        } catch (RollbackException e) {
            _logger.info("Denied user agent: " + userAgent + "; Username [" + userDto.getUsername() + "] is already taken.");
            return Response.status(Response.Status.CONFLICT).entity(Messages.CREATE_USER_WITH_NON_UNIQUE_NAME).build();
        } catch (URISyntaxException e) {
            _logger.info("Denied user agent: " + userAgent + "; could not convert return URI");
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        } finally {
            em.close();
        }
    }

    // TODO: make this hateos
    /**
     * This method creates a credit card instance under a user. A user can only ever have one credit card
     * associated with them at any one point. Authorization is required and can be provided with an authorization
     * token.
     * @param creditCard
     * @param userAgent
     * @param authToken
     * @return URI location to new payment
     */
    @POST
    @Path("/users/payment")
    @Consumes(MediaType.APPLICATION_XML)
    @Produces(MediaType.APPLICATION_XML)
    public Response createPayment(
            CreditCardDTO creditCard,
            @HeaderParam("user-agent") String userAgent,
            @HeaderParam("Authorization") String authToken) {

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
                    .location(new URI(_uri.getBaseUri() + "resources/users/" + foundUser.getUsername() + "/payment"))
                    .build();
        } catch (URISyntaxException e) {
            _logger.info("Denied user agent: " + userAgent + "; could not convert return URI");
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        } finally {
            em.close();
        }
    }

    /**
     * Returns an authorization token for a user. Once an authorization token is assigned to a user it is given
     * a timeout before the token becomes invalid. If this method is called while the user has an active token,
     * that token is returned, else a new token is generated and stored for the user.
     * @param userDTO
     * @param userAgent
     * @return Token found/generated by service for the user.
     */
    @POST
    @Path("/users/login")
    @Consumes(MediaType.APPLICATION_XML)
    @Produces(MediaType.APPLICATION_XML)
    public Response authenticateUser(
            UserDTO userDTO,
            @HeaderParam("user-agent") String userAgent) {

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

    // TODO: make hateos
    /**
     * Creates a new performer entity in the database of the service. Authentication is required and can be
     * provided through an authorization token.
     * @param performerDTO
     * @param userAgent
     * @param authToken
     * @return URI to created performer object
     */
    @POST
    @Path("/performers")
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
            PerformerDTO returnPerformerDTO = PerformerMapper.toDto(newPerformer);

            _sm.notifySubscribers(SubscriptionType.PERFORMER, newPerformer);
            _logger.info("Subscribers notified of new performer: " + newPerformer.getName());

            return Response
                    .status(Response.Status.OK)
                    .location(new URI(_uri.getBaseUri() + "resources/performers/" + newPerformer.getId()))
                    .entity(returnPerformerDTO)
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

            _sm.notifySubscribers(SubscriptionType.CONCERT, newConcert);
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

            _sm.notifySubscribers(SubscriptionType.PERFORMER_IMAGE, performer);
            _sm.notifySubscribersWithId(SubscriptionType.PERFORMER_IMAGE, performer, performer.getId());
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
     * Subscribes a user to notifications related to ANY new performer added to the database
     * @param response
     * @param userAgent
     * @param authToken
     */
    @GET
    @Path("/performers/getNotifications")
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