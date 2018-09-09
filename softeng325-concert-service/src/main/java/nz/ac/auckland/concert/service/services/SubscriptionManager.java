package nz.ac.auckland.concert.service.services;

import nz.ac.auckland.concert.service.domain.Concert;
import nz.ac.auckland.concert.service.domain.Performer;
import nz.ac.auckland.concert.service.domain.Types.SubscriptionType;

import javax.ws.rs.container.AsyncResponse;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Singleton class that manages subscription services and notifications for
 * subscribed users. An instance of this class will maintain a set of private
 * data structures mapping AsyncResponse objects to subscription notification
 * mechanisms that can be called by any requiring class.
 */
public class SubscriptionManager {

    private final ReentrantLock _performerLock = new ReentrantLock();
    private final ReentrantLock _concertLock = new ReentrantLock();
    private final ReentrantLock _imageLock = new ReentrantLock();

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
            synchronized (_performerLock) {
                _performerResponses.add(asyncResponse);
            }
        } else if (subscriptionType == SubscriptionType.CONCERT) {
            synchronized (_concertLock) {
                _concertResponses.add(asyncResponse);
            }
        } else if (subscriptionType == SubscriptionType.PERFORMER_IMAGE) {
            synchronized (_imageLock) {
                _imageResponses.add(asyncResponse);
            }
        }

    }

    public void addSubscriptionWithId(SubscriptionType subscriptionType, AsyncResponse asyncResponse, Long id) {

        if (subscriptionType == SubscriptionType.PERFORMER_IMAGE) {
            synchronized (_imageLock) {
                _imageResponsesWithIds.computeIfAbsent(id, k -> new ArrayList<>());
                _imageResponsesWithIds.get(id).add(asyncResponse);
            }
        }

    }

    public void notifySubscribers(SubscriptionType subscriptionType, Object object, String url) {

        if (subscriptionType == SubscriptionType.PERFORMER) {
            synchronized (_performerLock) {
                String name = ((Performer)object).getName();
                for (AsyncResponse response : _performerResponses) {
                    response.resume("There's a new performer in town! Check out " + name + " at: " + url);
                }
                _performerResponses.clear();
            }
        } else if (subscriptionType == SubscriptionType.CONCERT) {
            Concert concert = (Concert)object;
            synchronized (_concertLock) {
                for (AsyncResponse response : _concertResponses) {
                    response.resume("A new concert has been added called " + concert.getTitle() + " featuring " + Arrays.toString(concert.getPerformers().stream().map(Performer::getName).toArray()) + ", Check it out at: " + url);
                }
                _concertResponses.clear();
            }
        } else if (subscriptionType == SubscriptionType.PERFORMER_IMAGE) {
            synchronized (_imageLock) {
                Performer performer = (Performer)object;
                for (AsyncResponse response : _imageResponses) {
                    response.resume("A new image " + performer.getImageName() + " has been added for " + performer.getName() + ", check it out at: " + url);
                }
                _imageResponses.clear();
            }
        }
    }

    public void notifySubscribersWithId(SubscriptionType subscriptionType, Object object, Long id, String url) {

        if (subscriptionType == SubscriptionType.PERFORMER_IMAGE) {
            synchronized (_performerLock) {
                Performer performer = (Performer)object;

                if (_imageResponsesWithIds.get(id) == null) {
                    return;
                }

                for (AsyncResponse response : _imageResponsesWithIds.get(id)) {
                    response.resume("A new image " + performer.getImageName() + " has been added for " + performer.getName());
                }
                _imageResponsesWithIds.get(id).clear();
            }
        }

    }

}
