package nz.ac.auckland.concert.service.services;

import nz.ac.auckland.concert.service.domain.Concert;
import nz.ac.auckland.concert.service.domain.Performer;
import nz.ac.auckland.concert.service.domain.Types.SubscriptionType;

import javax.ws.rs.container.AsyncResponse;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Singleton class that manages subscription services and notifications for
 * subscribed users.
 */
public class SubscriptionManager {

    private static SubscriptionManager _instance = null;

    private List<AsyncResponse> _performerResponses;
    private List<AsyncResponse> _concertResponses;
    private List<AsyncResponse> _imageResponses;

    protected SubscriptionManager() {

        _performerResponses = new ArrayList<>();
        _concertResponses = new ArrayList<>();
        _imageResponses = new ArrayList<>();

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
        } else if (subscriptionType == SubscriptionType.CONCERT) {
            _concertResponses.add(asyncResponse);
        } else if (subscriptionType == SubscriptionType.PERFORMER_IMAGE) {
            _imageResponses.add(asyncResponse);
        }

    }

    public void notifySubscribers(SubscriptionType subscriptionType, Object object) {

        if (subscriptionType == SubscriptionType.PERFORMER) {
            String name = ((Performer)object).getName();
            for (AsyncResponse response : _performerResponses) {
                response.resume("There's a new performer in town! Check out " + name + " and hang tight for dates!");
            }
        } else if (subscriptionType == SubscriptionType.CONCERT) {
            Concert concert = (Concert)object;
            for (AsyncResponse response : _concertResponses) {
                response.resume("A new concert has been added called " + concert.getTitle() + " featuring " + Arrays.toString(concert.getPerformers().stream().map(Performer::getName).toArray()));
            }
        } else if (subscriptionType == SubscriptionType.PERFORMER_IMAGE) {
            String name = ((Performer)object).getName();
            for (AsyncResponse response : _imageResponses) {
                response.resume("A new image has been added for " + name);
            }
        }

    }

}
