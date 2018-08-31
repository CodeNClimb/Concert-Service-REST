package nz.ac.auckland.concert.service.services;

import nz.ac.auckland.concert.service.domain.Subscription;
import nz.ac.auckland.concert.service.domain.Types.SubscriptionType;
import nz.ac.auckland.concert.service.domain.User;

import javax.persistence.EntityManager;
import javax.ws.rs.container.AsyncResponse;
import java.util.ArrayList;
import java.util.List;

/**
 * Singleton class that manages subscription services and notifications for
 * subscribed users.
 */
public class SubscriptionManager {

    private static SubscriptionManager _instance = null;

    private List<AsyncResponse> _performerResponses;

    protected SubscriptionManager() {

        _performerResponses = new ArrayList<>();

    }

    public static SubscriptionManager instance() {
        if (_instance == null) {
            _instance = new SubscriptionManager();
        }
        return _instance;
    }

    public void addSubscription(SubscriptionType subscriptionType, AsyncResponse asyncResponse) {

        if (subscriptionType == SubscriptionType.PERFORMER) {
            _performerResponses.add(asyncResponse);
        }

    }

    public void notifySubscribers(SubscriptionType subscriptionType, String name) {

        if (subscriptionType == SubscriptionType.PERFORMER) {
            for (AsyncResponse response : _performerResponses) {
                response.resume("There's a new performer in town! Check out " + name + " and hang tight for dates!");
            }
        }

    }

}
