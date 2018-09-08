package nz.ac.auckland.concert.client.clientApp;

/**
 * Primitive form of getting subscription updates from a subscription service in the form of strings.
 * Only intended as a proof of implementation object.
 */
public class Subscription {

    private String _notification;
    private boolean _isUpdated;

    public Subscription() {
        _isUpdated = true;
    }

    public void updateSubscription(String message) {
        _notification = message;
        _isUpdated = true;
    }

    public String getSubscription() {
        return _notification;
    }

    public boolean isUnreadNotification() {
        return _isUpdated;
    }

}
