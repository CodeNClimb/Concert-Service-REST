package nz.ac.auckland.concert.client.service;

import nz.ac.auckland.concert.common.dto.*;

import javax.ws.rs.client.Client;
import javax.ws.rs.core.GenericType;
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

        Response res = _client.target(Config.LOCAL_SERVER_ADDRESS + "/resources/concerts").request().get();
        return res.readEntity(new GenericType<Set<ConcertDTO>>() {});
    }

    @Override
    public Set<PerformerDTO> getPerformers() throws ServiceException {

        Response res = _client.target(Config.LOCAL_SERVER_ADDRESS + "/resources/performers").request().get();
        return res.readEntity(new GenericType<Set<PerformerDTO>>() {});
    }

    @Override
    public UserDTO createUser(UserDTO newUser) throws ServiceException {
        return null;
    }

    @Override
    public UserDTO authenticateUser(UserDTO user) throws ServiceException {
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
