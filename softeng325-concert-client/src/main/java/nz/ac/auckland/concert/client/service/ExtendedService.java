package nz.ac.auckland.concert.client.service;

import nz.ac.auckland.concert.client.clientApp.Subscription;
import nz.ac.auckland.concert.common.dto.ConcertDTO;
import nz.ac.auckland.concert.common.dto.NewsItemDTO;
import nz.ac.auckland.concert.common.dto.PerformerDTO;
import nz.ac.auckland.concert.common.message.Messages;

import javax.ws.rs.ProcessingException;
import javax.ws.rs.ServiceUnavailableException;
import javax.ws.rs.client.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.NewCookie;
import javax.ws.rs.core.Response;

/**
 * This class contains all additional / optional functionality described by the assignment brief. It
 * contains all functionality provided by the DefaultService class by inheritance, and implements a
 * further set of methods.
 */
public class ExtendedService extends DefaultService {

    public ExtendedService() {
        _client = Config.POOLED_CLIENT;
    }

    public PerformerDTO createPerformer(PerformerDTO performerDTO) {
        try {
            Response res = _client
                    .target(Config.LOCAL_SERVER_ADDRESS + "/performers")
                    .request()
                    .header("Authorization", _authorizationToken) // Insert authorisation token
                    .accept(MediaType.APPLICATION_XML)
                    .post(Entity.xml(performerDTO));

            switch (res.getStatus()) {
                case 400: throw new ServiceException(res.readEntity(String.class));
                case 401: throw new ServiceException(res.readEntity(String.class));
                case 403: throw new ServiceException(res.readEntity(String.class));
            }

            res = _client // get performer given by location in response
                    .target(res.getLocation())
                    .request()
                    .get();

            return res.readEntity(PerformerDTO.class);

        } catch (ServiceUnavailableException | ProcessingException e) {
            e.printStackTrace();
            throw new ServiceException(Messages.SERVICE_COMMUNICATION_ERROR);
        }
    }

    public ConcertDTO createConcert(ConcertDTO concertDTO) {
        try {
            Response res = _client // Ask service to create concert
                    .target(Config.LOCAL_SERVER_ADDRESS + "/concerts")
                    .request()
                    .header("Authorization", _authorizationToken) // Insert authorisation token
                    .accept(MediaType.APPLICATION_XML)
                    .post(Entity.xml(concertDTO));

            switch (res.getStatus()) {
                case 400: throw new ServiceException(res.readEntity(String.class));
                case 401: throw new ServiceException(res.readEntity(String.class));
                case 403: throw new ServiceException(res.readEntity(String.class));
            }

            res = _client // Get concert from location sent in response
                    .target(res.getLocation())
                    .request()
                    .get();

            return res.readEntity(ConcertDTO.class);

        } catch (ServiceUnavailableException | ProcessingException e) {
            e.printStackTrace();
            throw new ServiceException(Messages.SERVICE_COMMUNICATION_ERROR);
        }
    }

    public PerformerDTO addImage(PerformerDTO performerDTO) {
        try {
            Response res = _client
                    .target(Config.LOCAL_SERVER_ADDRESS + "/images")
                    .request()
                    .header("Authorization", _authorizationToken) // Insert authorisation token
                    .accept(MediaType.APPLICATION_XML)
                    .put(Entity.xml(performerDTO));

            switch (res.getStatus()) {
                case 400: throw new ServiceException(res.readEntity(String.class));
                case 401: throw new ServiceException(res.readEntity(String.class));
                case 403: throw new ServiceException(res.readEntity(String.class));
            }

            return res.readEntity(PerformerDTO.class);
        } catch (ServiceUnavailableException | ProcessingException e) {
            e.printStackTrace();
            throw new ServiceException(Messages.SERVICE_COMMUNICATION_ERROR);
        }
    }


    public void subscribeToNewPerformers(Subscription subscription) {
        WebTarget target = _client.target(Config.LOCAL_SERVER_ADDRESS + "/performers/getNotifications");

        target.request()
                .header("Authorization", _authorizationToken) // Insert authorisation token
                .accept(MediaType.APPLICATION_XML)
                .async()
                .get(new InvocationCallback<NewsItemDTO>() {
            @Override
            public void completed(NewsItemDTO newsItemDTO) {
                subscription.updateSubscription(newsItemDTO.getNotifications());
                target.request()
                        .header("Authorization", _authorizationToken) // Insert authorisation token
                        .accept(MediaType.APPLICATION_XML)
                        .cookie(new NewCookie("latest-news", newsItemDTO.getCookie()))
                        .async()
                        .get(this);
            }

            @Override
            public void failed(Throwable throwable) {

            }
        });
    }

    public void subscribeToNewConcerts(Subscription subscription) {
        WebTarget target = _client.target(Config.LOCAL_SERVER_ADDRESS + "/concerts/getNotifications");

        target.request()
                .header("Authorization", _authorizationToken) // Insert authorisation token
                .accept(MediaType.APPLICATION_XML)
                .async()
                .get(new InvocationCallback<NewsItemDTO>() {
            @Override
            public void completed(NewsItemDTO newsItemDTO) {
                    subscription.updateSubscription(newsItemDTO.getNotifications());
                    target.request()
                            .header("Authorization", _authorizationToken) // Insert authorisation token
                            .accept(MediaType.APPLICATION_XML)
                            .cookie(new NewCookie("latest-news", newsItemDTO.getCookie()))
                            .async()
                            .get(this);
            }

            @Override
            public void failed(Throwable throwable) {

            }
        });
    }

    public void subscribeToNewImages(Subscription subscription) {
        WebTarget target = _client.target(Config.LOCAL_SERVER_ADDRESS + "/images/getNotifications/");

        target.request()
                .header("Authorization", _authorizationToken) // Insert authorisation token
                .accept(MediaType.APPLICATION_XML)
                .async()
                .get(new InvocationCallback<NewsItemDTO>() {
            @Override
            public void completed(NewsItemDTO newsItemDTO) {
                    subscription.updateSubscription(newsItemDTO.getNotifications());
                    target.request()
                        .header("Authorization", _authorizationToken) // Insert authorisation token
                        .accept(MediaType.APPLICATION_XML)
                        .cookie(new NewCookie("latest-news", newsItemDTO.getCookie()))
                        .async()
                        .get(this);
            }

            @Override
            public void failed(Throwable throwable) {

            }
        });

    }

    public void subscribeToNewImagesForPerformer(PerformerDTO performerDTO, Subscription subscription) {
        WebTarget target = _client.target(Config.LOCAL_SERVER_ADDRESS + "/images/getNotifications/" + performerDTO.getId());

        target.request()
                .header("Authorization", _authorizationToken) // Insert authorisation token
                .accept(MediaType.APPLICATION_XML)
                .async()
                .get(new InvocationCallback<NewsItemDTO>() {
            @Override
            public void completed(NewsItemDTO newsItemDTO) {
                    subscription.updateSubscription(newsItemDTO.getNotifications());
                    target.request()
                        .header("Authorization", _authorizationToken) // Insert authorisation token
                        .accept(MediaType.APPLICATION_XML)
                        .cookie(new NewCookie("latest-news", newsItemDTO.getCookie()))
                        .async()
                        .get(this);
            }

            @Override
            public void failed(Throwable throwable) {

            }
        });
    }

}
