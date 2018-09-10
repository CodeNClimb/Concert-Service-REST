package nz.ac.auckland.concert.client.clientApp;

import java.util.List;

/**
 * Primitive form of getting subscription updates from a subscription service in the form of strings.
 * Only intended as a proof of implementation object.
 */
public class Subscription {

    private List<String> _notifications;
    private boolean _isUpdated;

    public Subscription() {
        _isUpdated = false;
    }

    public void updateSubscription(List<String> message) {
        _notifications = message;
        _isUpdated = true;
    }

    public List<String> getSubscription() {
        _isUpdated = false;
        return _notifications;
    }

    public boolean isUnreadNotification() {
        return _isUpdated;
    }

}
