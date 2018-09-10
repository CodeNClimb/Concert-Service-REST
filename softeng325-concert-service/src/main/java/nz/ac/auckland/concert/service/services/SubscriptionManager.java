package nz.ac.auckland.concert.service.services;

import javafx.util.Pair;
import nz.ac.auckland.concert.common.dto.NewsItemDTO;
import nz.ac.auckland.concert.service.domain.Concert;
import nz.ac.auckland.concert.service.domain.Performer;
import nz.ac.auckland.concert.service.domain.Types.SubscriptionType;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import javax.ws.rs.client.AsyncInvoker;
import javax.ws.rs.container.AsyncResponse;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * Singleton class that manages subscription services and notifications for
 * subscribed users. An instance of this class will maintain a set of private
 * data structures mapping AsyncResponse objects to subscription notification
 * mechanisms that can be called by any requiring class.
 */
public class SubscriptionManager {

    private static SubscriptionManager _instance = null;

    private final ReentrantLock _performerLock = new ReentrantLock();
    private final ReentrantLock _concertLock = new ReentrantLock();
    private final ReentrantLock _imageLock = new ReentrantLock();

    private List<AsyncResponse> _performerResponses;
    private List<AsyncResponse> _concertResponses;
    private List<AsyncResponse> _imageResponses;
    private Map<Long, List<AsyncResponse>> _imageResponsesWithIds;

    // Buffers of latest notifications
    private List<Pair<Integer, String>> _recentPerformerNotifications;
    private List<Pair<Integer, String>> _recentConcertNotifications;
    private List<Pair<Integer, String>> _recentImageNotifications;
    private Map<Long, List<Pair<Integer, String>>> _recentImageWithIdRecentNotifications;

    protected SubscriptionManager() {

        _performerResponses = new ArrayList<>();
        _concertResponses = new ArrayList<>();
        _imageResponses = new ArrayList<>();

        _imageResponsesWithIds = new HashMap<>();

        _recentPerformerNotifications = new ArrayList<>();
        _recentConcertNotifications = new ArrayList<>();
        _recentImageNotifications = new ArrayList<>();
        _recentImageWithIdRecentNotifications = new HashMap<>();

    }

    public static SubscriptionManager instance() {
        if (_instance == null) {
            _instance = new SubscriptionManager();
        }
        return _instance;
    }

    public void addSubscription(SubscriptionType subscriptionType, AsyncResponse asyncResponse, String newsCookie) {

        if (subscriptionType == SubscriptionType.PERFORMER) {
            synchronized (_performerLock) {

                if (!updateIfUnseenNotifications(newsCookie, _recentPerformerNotifications, asyncResponse))
                    _performerResponses.add(asyncResponse);
            }
        } else if (subscriptionType == SubscriptionType.CONCERT) {
            synchronized (_concertLock) {

                if (!updateIfUnseenNotifications(newsCookie, _recentConcertNotifications, asyncResponse))
                    _concertResponses.add(asyncResponse);
            }
        } else if (subscriptionType == SubscriptionType.PERFORMER_IMAGE) {
            synchronized (_imageLock) {

                if (!updateIfUnseenNotifications(newsCookie, _recentImageNotifications, asyncResponse))
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
                String notification = "There's a new performer in town! Check out " + name + " at: " + url;

                storeAndRespond(notification, _recentPerformerNotifications, _performerResponses);
            }
        } else if (subscriptionType == SubscriptionType.CONCERT) {
            synchronized (_concertLock) {

                Concert concert = (Concert)object;
                String notification = "A new concert has been added called " + concert.getTitle() + " featuring " + Arrays.toString(concert.getPerformers().stream().map(Performer::getName).toArray()) + ", Check it out at: " + url;

                storeAndRespond(notification, _recentConcertNotifications, _concertResponses);
            }
        } else if (subscriptionType == SubscriptionType.PERFORMER_IMAGE) {

            synchronized (_imageLock) {
                Performer performer = (Performer)object;
                String notification = "A new image " + performer.getImageName() + " has been added for " + performer.getName() + ", check it out at: " + url;

                storeAndRespond(notification, _recentImageNotifications, _imageResponses);
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

    // Helper method for both storing recent notification in buffer and responding to all necessary respondents.
    private void storeAndRespond(String notification, List<Pair<Integer, String>> notificationList, List<AsyncResponse> responseList) {
        // Create hashcode of new notification
        int hashed = new HashCodeBuilder(37,39).append(notification).toHashCode();

        // Add has notification pair to list, remove tail if over 100.
        notificationList.add(0, new Pair<>(hashed, notification));
        if (notificationList.size() > 100)
            notificationList.remove(100);

        // Return all notifications for performers
        for (AsyncResponse response : responseList) {
            response.resume(new NewsItemDTO(Integer.toString(hashed), notification));
        }
        responseList.clear();
    }

    private boolean updateIfUnseenNotifications(String newsCookie, List<Pair<Integer, String>> notificationList, AsyncResponse asyncResponse) {
        if (newsCookie != null && // Cookie actually exists
                !notificationList.isEmpty() && // NotificationList actually has notifications in it
                notificationList.get(0).getKey() != Integer.parseInt(newsCookie)) { // User isn't already up to date with latest notification

            for (int i = 0; i < notificationList.size(); i++) {
                Pair<Integer, String> notification = notificationList.get(i);

                // If cookie equals the hashcode of some previous news story or we are at the end of the news
                // buffer then create sublist of notifications and send
                if (notification.getKey() == Integer.parseInt(newsCookie) || i == notificationList.size() - 1) {
                    List<Pair<Integer, String>> unseenNotifications = notificationList.subList(0, i);
                    List<String> notificationsToSend = unseenNotifications.stream().map(Pair::getValue).collect(Collectors.toList());

                    int hashed = new HashCodeBuilder(37,39).append(notificationsToSend.get(0)).toHashCode(); // hashcode is now the most recent of this list
                    asyncResponse.resume(new NewsItemDTO(Integer.toString(hashed), notificationsToSend));
                    return true; // Did send updates
                }
            }
        }

        return false; //  Didn't send updates
    }
}
