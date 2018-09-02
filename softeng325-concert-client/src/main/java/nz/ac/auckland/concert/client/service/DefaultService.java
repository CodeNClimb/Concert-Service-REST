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
import java.util.HashSet;
import java.util.Set;

public class DefaultService implements ConcertService {

    // Constants:
    // AWS S3 access credentials for concert images.
    protected static final String AWS_ACCESS_KEY_ID = Config.AWS_ACCESS_KEY_ID;
    protected static final String AWS_SECRET_ACCESS_KEY = Config.AWS_SECRET_ACCESS_KEY;

    protected static final int RETRIEVE_WINDOW_SIZE = 10;

    // Name of the S3 bucket that stores images.
    protected static final String AWS_BUCKET = Config.AWS_BUCKET;

    // Fields
    protected Client _client;
    protected String _authorizationToken;
    protected String _username;
    protected String _password;


    public DefaultService() {

        _client = Config.POOLED_CLIENT;

    }


    @Override
    public Set<ConcertDTO> getConcerts() throws ServiceException {

        // Use path parameters to get ranges of results
        int start = 0;
        int resultListLength = RETRIEVE_WINDOW_SIZE;

        Set<ConcertDTO> concerts = new HashSet<>();

        while (resultListLength == RETRIEVE_WINDOW_SIZE) { // While still receiving full window size sets
            try {
                String path = String.format("/resources/concerts?start=%d&size=%d", start, RETRIEVE_WINDOW_SIZE);
                Response res = _client.target(Config.LOCAL_SERVER_ADDRESS + path).request().get();

                Set<ConcertDTO> resultList = res.readEntity(new GenericType<Set<ConcertDTO>>() {});
                start += RETRIEVE_WINDOW_SIZE; // Crawl start along by window size
                resultListLength = resultList.size(); // Set as current size of result list, used in while predicate

                concerts.addAll(resultList);
            } catch (ServiceUnavailableException | ProcessingException e) {
                throw new ServiceException(Messages.SERVICE_COMMUNICATION_ERROR);
            }
        }

        return concerts;
    }

    @Override
    public Set<PerformerDTO> getPerformers() throws ServiceException {

        // Use path parameters to get ranges of results
        int start = 0;
        int resultListLength = RETRIEVE_WINDOW_SIZE;

        Set<PerformerDTO> performers = new HashSet<>();

        while (resultListLength == RETRIEVE_WINDOW_SIZE) { // While still receiving full window size sets
            try {
                String path = String.format("/resources/performers?start=%d&size=%d", start, RETRIEVE_WINDOW_SIZE);
                Response res = _client.target(Config.LOCAL_SERVER_ADDRESS + path).request().get();

                Set<PerformerDTO> resultList = res.readEntity(new GenericType<Set<PerformerDTO>>() {});
                start += RETRIEVE_WINDOW_SIZE; // Crawl start along by window size
                resultListLength = resultList.size(); // Set as current size of result list, used in while predicate

                performers.addAll(resultList);
            } catch (ServiceUnavailableException | ProcessingException e) {
                throw new ServiceException(Messages.SERVICE_COMMUNICATION_ERROR);
            }
        }

        return performers;
    }

    @Override
    public UserDTO createUser(UserDTO newUser) throws ServiceException {

        try {
            Response res = _client.target(Config.LOCAL_SERVER_ADDRESS + "/resources/users").request()
                    .accept(MediaType.APPLICATION_XML).post(Entity.xml(newUser));

            switch(res.getStatus()) {
                case 400: throw new ServiceException(res.readEntity(String.class)); // Incomplete fields
                case 409: throw new ServiceException(res.readEntity(String.class)); // Username conflict
            }

            // Store auth. details
            _authorizationToken = res.getHeaderString("Authorization");
            _username = newUser.getUsername();
            _password = newUser.getPassword();

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
                case 400: throw new ServiceException(res.readEntity(String.class)); // Missing username and/or password
                case 401: throw new ServiceException(res.readEntity(String.class)); // Wrong password
                case 404: throw new ServiceException(res.readEntity(String.class)); // No such user exists
            }

            // Store auth. details
            _authorizationToken = res.getHeaderString("Authorization");
            _username = user.getUsername();
            _password = user.getPassword();

            return res.readEntity(UserDTO.class);
        } catch (ServiceUnavailableException | ProcessingException e) {
            throw new ServiceException(Messages.SERVICE_COMMUNICATION_ERROR);
        }
    }

    // TODO : Fix error throwing with silly amazon s3 stuff
    // TODO : Move to service
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

        try {
            Response res = _client
                    .target(Config.LOCAL_SERVER_ADDRESS + "/resources/reserve")
                    .request()
                    .header("Authorization", _authorizationToken) // Insert authorisation token
                    .accept(MediaType.APPLICATION_XML)
                    .post(Entity.xml(reservationRequest));

            switch(res.getStatus()) {
                case 400: throw new ServiceException(res.readEntity(String.class));
                case 401: throw new ServiceException(res.readEntity(String.class));
                case 403: throw new ServiceException(res.readEntity(String.class));
                case 404: throw new ServiceException(res.readEntity(String.class));
                case 409: throw new ServiceException(res.readEntity(String.class));

            }

            return res.readEntity(ReservationDTO.class);
        } catch (ServiceUnavailableException | ProcessingException e) {
            throw new ServiceException(Messages.SERVICE_COMMUNICATION_ERROR);
        }
    }

    @Override
    public void confirmReservation(ReservationDTO reservation) throws ServiceException {

        try {
            Response res = _client
                    .target(Config.LOCAL_SERVER_ADDRESS + "/resources/reserve/book")
                    .request()
                    .header("Authorization", _authorizationToken) // Insert authorisation token
                    .accept(MediaType.APPLICATION_XML)
                    .post(Entity.xml(reservation));

            switch (res.getStatus()) {
                case 401: throw new ServiceException(res.readEntity(String.class));
                case 402: throw new ServiceException(res.readEntity(String.class));
                case 403: throw new ServiceException(res.readEntity(String.class));
                case 408: throw new ServiceException(res.readEntity(String.class));
            }
        } catch (ServiceUnavailableException | ProcessingException e) {
            throw new ServiceException(Messages.SERVICE_COMMUNICATION_ERROR);
        }
    }

    @Override
    public void registerCreditCard(CreditCardDTO creditCard) throws ServiceException {

        try {
            Response res = _client
                    .target(Config.LOCAL_SERVER_ADDRESS + "/resources/users/payment")
                    .request()
                    .header("Authorization", _authorizationToken) // Insert authorisation token
                    .accept(MediaType.APPLICATION_XML)
                    .post(Entity.xml(creditCard));

            switch (res.getStatus()) {
                case 401: throw new ServiceException(res.readEntity(String.class));
                case 403: throw new ServiceException(res.readEntity(String.class));
            }
        } catch (ServiceUnavailableException | ProcessingException e) {
            throw new ServiceException((Messages.SERVICE_COMMUNICATION_ERROR));
        }
    }

    @Override
    public Set<BookingDTO> getBookings() throws ServiceException {

        // Use path parameters to get ranges of results
        int start = 0;
        int resultListLength = RETRIEVE_WINDOW_SIZE;

        Set<BookingDTO> bookings = new HashSet<>();

        while (resultListLength == RETRIEVE_WINDOW_SIZE) { // While still receiving full window size sets
            try {
                String path = String.format("/resources/users/book?start=%d&size=%d", start, RETRIEVE_WINDOW_SIZE);
                Response res = _client
                        .target(Config.LOCAL_SERVER_ADDRESS + path)
                        .request()
                        .header("Authorization", _authorizationToken) // Insert authorisation token
                        .get();

                switch (res.getStatus()) {
                    case 401: throw new ServiceException(res.readEntity(String.class));
                    case 403: throw new ServiceException(res.readEntity(String.class));
                }

                Set<BookingDTO> resultList = res.readEntity(new GenericType<Set<BookingDTO>>() {});
                start += RETRIEVE_WINDOW_SIZE; // Crawl start along by window size
                resultListLength = resultList.size(); // Set as current size of result list, used in while predicate

                bookings.addAll(resultList);
            } catch (ServiceUnavailableException | ProcessingException e) {
                throw new ServiceException((Messages.SERVICE_COMMUNICATION_ERROR));
            }
        }

        return bookings;
    }
}
