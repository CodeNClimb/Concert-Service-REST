package nz.ac.auckland.concert.service.services;

import nz.ac.auckland.concert.common.dto.BookingDTO;
import nz.ac.auckland.concert.common.dto.CreditCardDTO;
import nz.ac.auckland.concert.common.dto.UserDTO;
import nz.ac.auckland.concert.common.message.Messages;
import nz.ac.auckland.concert.service.domain.Booking;
import nz.ac.auckland.concert.service.domain.Mappers.BookingMapper;
import nz.ac.auckland.concert.service.domain.Mappers.CreditCardMapper;
import nz.ac.auckland.concert.service.domain.Mappers.UserMapper;
import nz.ac.auckland.concert.service.domain.Token;
import nz.ac.auckland.concert.service.domain.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;
import javax.persistence.RollbackException;
import javax.persistence.TypedQuery;
import javax.ws.rs.*;
import javax.ws.rs.core.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Path("/users")
public class UserResource {

    private static final Logger _logger = LoggerFactory.getLogger(ConcertResource.class);

    private static final long AUTHENTICATION_TIMEOUT_MINUTES = 5; // 5 minutes

    @Context // Information about the service hosted URI
    private static UriInfo _uri;

    private final PersistenceManager _pm; // Persistence

    public UserResource() {

        _pm = PersistenceManager.instance();
    }

    /**
     * Retrieves a single user given a username. Requires authentication by authorization token.
     * @param userAgent
     * @param username
     * @return UserDTO
     */
    @GET
    @Path("/{username}")
    @Produces(MediaType.APPLICATION_XML)
    public Response getUser(
            @HeaderParam("user-agent") String userAgent,
            @HeaderParam("Authorization") String authToken,
            @PathParam("username") String username) {

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

            User user = em.find(User.class, username);
            UserDTO returnUser = UserMapper.toDTO(user);

            return Response
                    .status(Response.Status.OK)
                    .entity(returnUser)
                    .build();
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
    @Path("/book")
    @Consumes(MediaType.APPLICATION_XML)
    @Produces(MediaType.APPLICATION_XML)
    public Response getBookings(
            @HeaderParam("user-agent") String userAgent,
            @HeaderParam("Authorization") String authToken,
            @DefaultValue("0") @QueryParam("start") int start,
            @DefaultValue("10") @QueryParam("size") int size) {

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
                    .location(new URI(_uri.getBaseUri() + String.format("users/book?start=%d&size=%d", start + size, size))) // Next batch of bookings
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
     * This method creates and stores a new user entity in the database of the service, given a userDTO object sent
     * from the client. The new user must have a unique username, and the user is returned an authorization token
     * when successful account creation occurs.
     * @param userDto
     * @param userAgent
     * @return URI to created user
     */
    @POST
    @Path("/")
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
            tx.begin(); // Act of creating user and storing corresponding token is atomic

            User newUser = UserMapper.toDomain(userDto);
            em.persist(newUser);

            String token = generateUserToken();
            em.persist(new Token(newUser, token, LocalDateTime.now().plus(Duration.ofMinutes(AUTHENTICATION_TIMEOUT_MINUTES)))); // Tokens expiry id .now() plus timeout duration

            tx.commit();
            tx.begin();

            User storedUser = em.find(User.class, userDto.getUsername());
            UserDTO returnDTO = UserMapper.toDTO(storedUser);
            _logger.info("Successfully created new user: [" + storedUser.getUsername() + ", " + storedUser.getFirstName() + ", " + storedUser.getLastName() + "]; Reply to user agent :" + userAgent);

            return Response
                    .status(Response.Status.OK)
                    .header("Authorization", token) // place auth token in header under Authorization:
                    .location(new URI(_uri.getBaseUri() + "users/" + returnDTO.getUsername())) // Return location of new user
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
    @Path("/payment")
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
                    .build();
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
    @Path("/login")
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

            EntityTransaction tx = em.getTransaction();
            tx.begin(); // Ensure the act of reading token status then rewriting (if needed) is atomic - ensures no conflicting token generation

            // Login is correct, so we return token either newly generated or already one stored if still active.
            Token token = em.find(Token.class, foundUser.getUsername()); // One token for one user

            // either token does not exist for this user or has timed out - if has timed out service should provide a new one
            String tokenString;
            if (token == null || (token.getExpiry().isBefore(LocalDateTime.now()))) { // Token is null OR Token has timed out

                if (token != null) // Remove the current token if one exists
                {
                    em.remove(token);
                }

                tokenString = generateUserToken(); // Overwrite / rewrite token

                // Place new token into db
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
                    .header("Authorization", tokenString) // place auth token in header under Authorization
                    .entity(UserMapper.toDTO(foundUser))
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

    private String generateUserToken() {
        return UUID.randomUUID().toString();
    }
}
