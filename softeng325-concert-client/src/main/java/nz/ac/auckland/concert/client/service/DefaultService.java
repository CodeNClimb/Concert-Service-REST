package nz.ac.auckland.concert.client.service;

import nz.ac.auckland.concert.common.dto.*;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.core.Response;
import java.awt.*;
import java.util.Set;

public class DefaultService implements ConcertService {

    private static Client _client;


    public DefaultService() {

        // TODO: Probably find a better way of doing this
        _client = ClientBuilder.newClient();

    }


    @Override
    public Set<ConcertDTO> getConcerts() throws ServiceException {

        Response res = _client.target("http://localhost:10000/services/concerts/").request().get();
        System.out.println(res.getStatus());

        return null;
    }

    @Override
    public Set<PerformerDTO> getPerformers() throws ServiceException {
        return null;
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
