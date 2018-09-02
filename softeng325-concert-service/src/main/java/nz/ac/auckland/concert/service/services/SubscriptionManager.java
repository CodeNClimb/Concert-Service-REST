package nz.ac.auckland.concert.service.services;

import nz.ac.auckland.concert.service.domain.Concert;
import nz.ac.auckland.concert.service.domain.Performer;
import nz.ac.auckland.concert.service.domain.Subscription;
import nz.ac.auckland.concert.service.domain.Types.SubscriptionType;

import javax.ws.rs.container.AsyncResponse;
import java.util.*;

/**
 * Singleton class that manages subscription services and notifications for
 * subscribed users.
 */
public class SubscriptionManager {

    private static SubscriptionManager _instance = null;

    private List<AsyncResponse> _performerResponses;
    private List<AsyncResponse> _concertResponses;
    private List<AsyncResponse> _imageResponses;

    private Map<Long, List<AsyncResponse>> _imageResponsesWithIds;

    protected SubscriptionManager() {

        _performerResponses = new ArrayList<>();
        _concertResponses = new ArrayList<>();
        _imageResponses = new ArrayList<>();

        _imageResponsesWithIds = new HashMap<>();

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

    public void addSubscriptionWithId(SubscriptionType subscriptionType, AsyncResponse asyncResponse, Long id) {

        if (subscriptionType == SubscriptionType.PERFORMER_IMAGE) {
            if (_imageResponsesWithIds.get(id) == null) {
                _imageResponsesWithIds.put(id, new ArrayList<>());
            }
            _imageResponsesWithIds.get(id).add(asyncResponse);
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
            Performer performer = (Performer)object;
            for (AsyncResponse response : _imageResponses) {
                response.resume("A new image " + performer.getImageName() + " has been added for " + performer.getName());
            }
        }

    }

    public void notifySubscribersWithId(SubscriptionType subscriptionType, Object object, Long id) {

        if (subscriptionType == SubscriptionType.PERFORMER_IMAGE) {
            Performer performer = (Performer)object;

            if (_imageResponsesWithIds.get(id) == null) {
                return;
            }

            for (AsyncResponse response : _imageResponsesWithIds.get(id)) {
                response.resume("A new image " + performer.getImageName() + " has been added for " + performer.getName());
            }
        }

    }

}
