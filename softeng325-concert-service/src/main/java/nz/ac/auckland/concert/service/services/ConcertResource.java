package nz.ac.auckland.concert.service.services;

import nz.ac.auckland.concert.common.dto.ConcertDTO;
import nz.ac.auckland.concert.common.dto.PerformerDTO;
import nz.ac.auckland.concert.common.dto.UserDTO;
import nz.ac.auckland.concert.service.domain.Concert;
import nz.ac.auckland.concert.service.domain.Mappers.ConcertMapper;
import nz.ac.auckland.concert.service.domain.Mappers.PerformerMapper;
import nz.ac.auckland.concert.service.domain.Mappers.UserMapper;
import nz.ac.auckland.concert.service.domain.Performer;
import nz.ac.auckland.concert.service.domain.Token;
import nz.ac.auckland.concert.service.domain.User;

import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;
import javax.persistence.RollbackException;
import javax.persistence.TypedQuery;
import javax.ws.rs.*;
import javax.ws.rs.core.GenericEntity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Path("/resources")
public class ConcertResource {

    private static final long AUTHENTICATION_TIMEOUT_MINUTES = 5; // 5 minutes

    private final PersistenceManager _pm;

    public ConcertResource() {

        _pm = PersistenceManager.instance();

    }

    // TODO: LOGGINGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGG

    @GET
    @Path("/concerts")
    @Produces(MediaType.APPLICATION_XML)
    public Response getConcerts() {

        EntityManager em = _pm.createEntityManager();

        try {
            EntityTransaction tx = em.getTransaction();
            tx.begin();

            TypedQuery<Concert> q = em.createQuery("SELECT c FROM Concert c", Concert.class);
            List<Concert> concerts = q.getResultList();

            tx.commit();

            List<ConcertDTO> concertDTOs = concerts.stream().map(ConcertMapper::doDto).collect(Collectors.toList());
            GenericEntity<List<ConcertDTO>> entity = new GenericEntity<List<ConcertDTO>>(concertDTOs) {};

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
    public Response getPerformers() {

        EntityManager em = _pm.createEntityManager();

        try {
            EntityTransaction tx = em.getTransaction();
            tx.begin();

            TypedQuery<Performer> q = em.createQuery("SELECT p FROM Performer p", Performer.class);
            List<Performer> performers = q.getResultList();

            tx.commit();

            List<PerformerDTO> performerDTOs = performers.stream().map(PerformerMapper::toDto).collect(Collectors.toList());
            GenericEntity<List<PerformerDTO>> entity = new GenericEntity<List<PerformerDTO>>(performerDTOs) {};

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
    public Response createUser(UserDTO userDto) {

        if (userDto.getLastname() == null || userDto.getFirstname() == null || // If any necessary fields are not given
                userDto.getUsername() == null || userDto.getPassword() == null)
            return Response.status(422).entity(userDto).build(); // javax doesn't seem to contain 422 - Unprocessable Entity error code

        EntityManager em = _pm.createEntityManager();

        try {
            EntityTransaction tx = em.getTransaction();
            tx.begin();

            User newUser = UserMapper.toDomain(userDto);
            em.persist(newUser);

            String token = generateUserToken();
            em.persist(new Token(newUser, token, LocalDateTime.now())); // Create and persist auth. token for user

            tx.commit();
            tx.begin();

            User storedUser = em.find(User.class, userDto.getUsername());
            UserDTO returnDTO = UserMapper.toDTO(storedUser);

            return Response
                    .status(Response.Status.OK)
                    .header("Authorization", token) // place auth token in header under Authorization:
                    .entity(returnDTO)
                    .build();
        } catch (RollbackException e) {
            return Response.status(Response.Status.CONFLICT).entity(userDto).build();
        } finally {
            em.close();
        }
    }

    @POST
    @Path("/users/login")
    @Consumes(MediaType.APPLICATION_XML)
    @Produces(MediaType.APPLICATION_XML)
    public Response authenticateUser(UserDTO userDTO) {

        if (userDTO.getUsername() == null || userDTO.getPassword() == null) // If either username or password is empty
            return Response.status(422).entity(userDTO).build(); // javax doesn't seem to contain 422 - Unprocessable Entity error code

        EntityManager em = _pm.createEntityManager();

        try {
            // Check login details are correct
            User foundUser = em.find(User.class, userDTO.getUsername());
            if (foundUser == null) {// No user found
                return Response.status(Response.Status.NOT_FOUND).entity(userDTO).build();
            } else if (!foundUser.getPassword().equals(userDTO.getPassword())) { // Login credentials incorrect
                return Response.status(Response.Status.UNAUTHORIZED).entity(userDTO).build();
            }

            // Login is correct, so we return token either newly generated or already one stored if still active.
            Token token = em.find(Token.class, foundUser.getUsername()); // One token for one user

            // either token does not exist for this user or has timed out - if has timed out service should provide a new one
            String tokenString;
            if (token == null || // TODO: Test with expired timestamp
                    (ChronoUnit.MINUTES.between(token.getTimeStamp(), LocalDateTime.now())) > AUTHENTICATION_TIMEOUT_MINUTES) {

                if (token != null) // Remove the current token if one exists
                    em.remove(token);

                tokenString = generateUserToken();

                // Place new token into db
                EntityTransaction tx = em.getTransaction();
                tx.begin();

                Token tokenToPlace = new Token(foundUser, tokenString, LocalDateTime.now());
                em.persist(tokenToPlace);

                tx.commit();
            } else { // Token stored in db both exists and is still valid
                tokenString = token.getToken(); // Add existing token to response
            }

            return Response
                    .status(Response.Status.OK)
                    .header("Authorization", tokenString) // place auth token in header under Authorization:
                    .entity(UserMapper.toDTO(foundUser))
                    .build();
        } finally {
            em.close();
        }
    }

    // TODO: use HTTP 401 for timed out resources in future

    private String generateUserToken() {
        return UUID.randomUUID().toString();
    }

}
