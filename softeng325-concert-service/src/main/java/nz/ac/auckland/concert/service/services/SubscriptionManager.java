package nz.ac.auckland.concert.service.services;

import nz.ac.auckland.concert.service.domain.User;

/**
 * Singleton class that manages subscription services and notifications for
 * subscribed users.
 */
public class SubscriptionManager {

    private static SubscriptionManager _instance = null;

    private final PersistenceManager _pm;


    protected SubscriptionManager() {
        _pm = PersistenceManager.instance();
    }

    public static SubscriptionManager instance() {
        if (_instance == null) {
            _instance = new SubscriptionManager();
        }
        return _instance;
    }

    public boolean addSubscription(Class t, User user) {
        return false;
    }

    public boolean notifySubscribers(Class t) {
        return false;
    }

}
