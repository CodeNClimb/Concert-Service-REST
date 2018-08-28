package nz.ac.auckland.concert.client.service;

import nz.ac.auckland.concert.common.dto.*;
import nz.ac.auckland.concert.common.message.Messages;

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

    private static Client _client;


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
    public UserDTO createUser(UserDTO newUser) throws ServiceException { // TODO: Authenticate user when implemented//////////////////////////////////////////////////////

        try {
            Response res = _client.target(Config.LOCAL_SERVER_ADDRESS + "/resources/users").request()
                    .accept(MediaType.APPLICATION_XML).post(Entity.xml(newUser));

            switch(res.getStatus()) {
                case 409: throw new ServiceException(Messages.CREATE_USER_WITH_NON_UNIQUE_NAME); // Username conflict
                case 422: throw new ServiceException(Messages.CREATE_USER_WITH_MISSING_FIELDS); // Incomplete fields
            }

            return res.readEntity(UserDTO.class);
        } catch (ServiceUnavailableException | ProcessingException e) {
            throw new ServiceException(Messages.SERVICE_COMMUNICATION_ERROR);
        }
    }

    @Override
    public UserDTO authenticateUser(UserDTO user) throws ServiceException {

        Response res = _client.target(Config.LOCAL_SERVER_ADDRESS + "/resources/users/login").request()
                .accept(MediaType.APPLICATION_XML).post(Entity.xml(user));

        System.out.println("STATUS: " + res.getStatus());

        return null;
    }

    @Override
    public Image getImageForPerformer(PerformerDTO performer) throws ServiceException {
        return null;
    }

    @Override
    public ReservationDTO reserveSeats(ReservationRequestDTO reservationRequest) throws ServiceException {
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
