package nz.ac.auckland.concert.client.service;

import nz.ac.auckland.concert.client.clientApp.Subscription;
import nz.ac.auckland.concert.common.dto.ConcertDTO;
import nz.ac.auckland.concert.common.dto.PerformerDTO;
import nz.ac.auckland.concert.common.message.Messages;

import javax.ws.rs.ProcessingException;
import javax.ws.rs.ServiceUnavailableException;
import javax.ws.rs.client.AsyncInvoker;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.InvocationCallback;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

/**
 * This class contains all additional / optional functionality described by the assignment brief. It
 * contains all functionality provided by the DefaultService class by inheritance, and implements a
 * further set of methods.
 */
public class ExtendedService extends DefaultService {

    private boolean _staySubscribed;

    public ExtendedService() {
        _client = Config.POOLED_CLIENT;
        _staySubscribed = false;
    }

    public PerformerDTO createPerformer(PerformerDTO performerDTO) {
        try {
            Response res = _client
                    .target(Config.LOCAL_SERVER_ADDRESS + "/resources/performers")
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
                    .target(Config.LOCAL_SERVER_ADDRESS + "/resources/concerts")
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
                    .target(Config.LOCAL_SERVER_ADDRESS + "/resources/images")
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
        _staySubscribed = true;
        AsyncInvoker invoker = _client.target(Config.LOCAL_SERVER_ADDRESS + "/resources/performers/getNotifications")
                .request()
                .header("Authorization", _authorizationToken) // Insert authorisation token
                .accept(MediaType.APPLICATION_XML)
                .async();

        invoker.get(new InvocationCallback<String>() {
            @Override
            public void completed(String s) {
                if (_staySubscribed) {
                    subscription.updateSubscription(s);
                    invoker.get(this);
                }
            }

            @Override
            public void failed(Throwable throwable) {
                if (_staySubscribed) {
                    throwable.printStackTrace();
                    invoker.get(this);
                }
            }
        });
    }

    public void subscribeToNewConcerts(Subscription subscription) {
        _staySubscribed = true;
        AsyncInvoker invoker = _client
                .target(Config.LOCAL_SERVER_ADDRESS + "/resources/concerts/getNotifications")
                .request()
                .header("Authorization", _authorizationToken) // Insert authorisation token
                .accept(MediaType.APPLICATION_XML)
                .async();

        invoker.get(new InvocationCallback<String>() {
            @Override
            public void completed(String s) {
                if (_staySubscribed) {
                    subscription.updateSubscription(s);
                    invoker.get(this);
                }
            }

            @Override
            public void failed(Throwable throwable) {
                if (_staySubscribed) {
                    throwable.printStackTrace();
                    invoker.get(this);
                }
            }
        });
    }

    public void subscribeToNewImages(Subscription subscription) {
        _staySubscribed = true;
        AsyncInvoker invoker = _client
                .target(Config.LOCAL_SERVER_ADDRESS + "/resources/images/getNotifications/")
                .request()
                .header("Authorization", _authorizationToken) // Insert authorisation token
                .accept(MediaType.APPLICATION_XML)
                .async();

        invoker.get(new InvocationCallback<String>() {
            @Override
            public void completed(String s) {
                if (_staySubscribed) {
                    subscription.updateSubscription(s);
                    invoker.get(this);
                }
            }

            @Override
            public void failed(Throwable throwable) {
                if (_staySubscribed) {
                    throwable.printStackTrace();
                    invoker.get(this);
                }
            }
        });

    }

    public void subscribeToNewImagesForPerformer(PerformerDTO performerDTO, Subscription subscription) {
        AsyncInvoker invoker = _client
                .target(Config.LOCAL_SERVER_ADDRESS + "/resources/images/getNotifications/" + performerDTO.getId())
                .request()
                .header("Authorization", _authorizationToken) // Insert authorisation token
                .accept(MediaType.APPLICATION_XML)
                .async();

        invoker.get(new InvocationCallback<String>() {
            @Override
            public void completed(String s) {
                if (_staySubscribed) {
                    subscription.updateSubscription(s);
                    invoker.get(this);
                }
            }

            @Override
            public void failed(Throwable throwable) {
                if (_staySubscribed) {
                    throwable.printStackTrace();
                    invoker.get(this);
                }
            }
        });
    }

    public void unsubscribe() {
    }

}
