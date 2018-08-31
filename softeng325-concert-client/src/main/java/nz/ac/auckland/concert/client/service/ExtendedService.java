package nz.ac.auckland.concert.client.service;

import nz.ac.auckland.concert.common.dto.PerformerDTO;
import nz.ac.auckland.concert.common.message.Messages;

import javax.ws.rs.ProcessingException;
import javax.ws.rs.ServiceUnavailableException;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

public class ExtendedService extends DefaultService {

    // TODO: error handling for these boys

    public PerformerDTO createPerformer(PerformerDTO performerDTO) {

        try {
            Response res = _client
                    .target(Config.LOCAL_SERVER_ADDRESS + "/resources/performers")
                    .request()
                    .header("Authorization", _authorizationToken) // Insert authorisation token
                    .accept(MediaType.APPLICATION_XML)
                    .post(Entity.xml(performerDTO));

            return res.readEntity(PerformerDTO.class);

        } catch (ServiceUnavailableException | ProcessingException e) {
            e.printStackTrace();
            throw new ServiceException(Messages.SERVICE_COMMUNICATION_ERROR);
        }
    }


    public String subscribeToNewPerformers() {
        Response resw = _client
                .target(Config.LOCAL_SERVER_ADDRESS + "/resources/performers/getNotifications")
                .request()
                .header("Authorization", _authorizationToken) // Insert authorisation token
                .accept(MediaType.APPLICATION_XML)
                .get();

        return null;
    }

}
