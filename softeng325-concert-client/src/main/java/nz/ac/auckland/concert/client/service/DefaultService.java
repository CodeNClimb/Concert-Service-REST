package nz.ac.auckland.concert.client.service;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.AmazonS3Exception;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectInputStream;
import nz.ac.auckland.concert.common.dto.*;
import nz.ac.auckland.concert.common.message.Messages;

import javax.imageio.ImageIO;
import javax.ws.rs.ProcessingException;
import javax.ws.rs.ServiceUnavailableException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.awt.*;
import java.util.Set;

public class DefaultService implements ConcertService {

    // Constants:
    // AWS S3 access credentials for concert images.
    private static final String AWS_ACCESS_KEY_ID = Config.AWS_ACCESS_KEY_ID;
    private static final String AWS_SECRET_ACCESS_KEY = Config.AWS_SECRET_ACCESS_KEY;

    // Name of the S3 bucket that stores images.
    private static final String AWS_BUCKET = Config.AWS_BUCKET;

    // Fields
    private Client _client;
    private String _authorizationToken;


    public DefaultService() {

        _client = Config.DEFAULT_CLIENT;

    }


    @Override
    public Set<ConcertDTO> getConcerts() throws ServiceException {

        try {
            Response res = _client.target(Config.LOCAL_SERVER_ADDRESS + "/resources/concerts").request().get();
            return res.readEntity(new GenericType<Set<ConcertDTO>>() {});
        } catch (ServiceUnavailableException | ProcessingException e) {
            throw new ServiceException(Messages.SERVICE_COMMUNICATION_ERROR);
        }
    }

    @Override
    public Set<PerformerDTO> getPerformers() throws ServiceException {

        try {
            Response res = _client.target(Config.LOCAL_SERVER_ADDRESS + "/resources/performers").request().get();
            return res.readEntity(new GenericType<Set<PerformerDTO>>() {});
        } catch (ServiceUnavailableException | ProcessingException e) {
            throw new ServiceException(Messages.SERVICE_COMMUNICATION_ERROR);
        }
    }

    @Override
    public UserDTO createUser(UserDTO newUser) throws ServiceException {

        try {
            Response res = _client.target(Config.LOCAL_SERVER_ADDRESS + "/resources/users").request()
                    .accept(MediaType.APPLICATION_XML).post(Entity.xml(newUser));

            switch(res.getStatus()) {
                case 409: throw new ServiceException(Messages.CREATE_USER_WITH_NON_UNIQUE_NAME); // Username conflict
                case 422: throw new ServiceException(Messages.CREATE_USER_WITH_MISSING_FIELDS); // Incomplete fields
            }

            _authorizationToken = res.getHeaderString("Authorization"); // Store token

            return res.readEntity(UserDTO.class);
        } catch (ServiceUnavailableException | ProcessingException e) {
            throw new ServiceException(Messages.SERVICE_COMMUNICATION_ERROR);
        }
    }

    @Override
    public UserDTO authenticateUser(UserDTO user) throws ServiceException {

        try {
            Response res = _client.target(Config.LOCAL_SERVER_ADDRESS + "/resources/users/login").request()
                    .accept(MediaType.APPLICATION_XML).post(Entity.xml(user));

            switch(res.getStatus()) {
                case 401: throw new ServiceException(Messages.AUTHENTICATE_USER_WITH_ILLEGAL_PASSWORD); // Wrong password
                case 404: throw new ServiceException(Messages.AUTHENTICATE_NON_EXISTENT_USER); // No such user exists
                case 422: throw new ServiceException(Messages.AUTHENTICATE_USER_WITH_MISSING_FIELDS); // Missing username and/or password
            }

            _authorizationToken = res.getHeaderString("Authorization"); // Update/Store token

            return res.readEntity(UserDTO.class);
        } catch (ServiceUnavailableException | ProcessingException e) {
            throw new ServiceException(Messages.SERVICE_COMMUNICATION_ERROR);
        }
    }

    // TODO : Fix error throwing with silly amazon s3 stuff
    @Override
    public Image getImageForPerformer(PerformerDTO performer) throws ServiceException {

        try {
            BasicAWSCredentials awsCredentials = new BasicAWSCredentials(AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY);
            AmazonS3 s3 = AmazonS3ClientBuilder
                    .standard()
                    .withRegion(Regions.AP_SOUTHEAST_2)
                    .withCredentials(new AWSStaticCredentialsProvider(awsCredentials))
                    .build();

            S3Object object;
            try {
                object = s3.getObject(AWS_BUCKET, performer.getImageName());
            } catch (AmazonS3Exception e) {
                throw new ServiceException(Messages.NO_IMAGE_FOR_PERFORMER);
            }
            S3ObjectInputStream s3is = object.getObjectContent();

            return ImageIO.read(s3is);
        } catch (Exception e) {
            throw new ServiceException(Messages.SERVICE_COMMUNICATION_ERROR);
        }
    }

    @Override
    public ReservationDTO reserveSeats(ReservationRequestDTO reservationRequest) throws ServiceException {

        Response res = _client.target(Config.LOCAL_SERVER_ADDRESS + "/resources/reserve").request()
                .accept(MediaType.APPLICATION_XML).post(Entity.xml(reservationRequest));

        ReservationDTO returned = res.readEntity(ReservationDTO.class);
        System.out.println(returned.getId());
        System.out.println(returned.getSeats());
        System.out.println(returned.getReservationRequest().getConcertId());

        return null;
    }

    @Override
    public void confirmReservation(ReservationDTO reservation) throws ServiceException {

    }

    @Override
    public void registerCreditCard(CreditCardDTO creditCard) throws ServiceException {

    }

    @Override
    public Set<BookingDTO> getBookings() throws ServiceException {
        return null;
    }
}
