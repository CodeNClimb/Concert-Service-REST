package nz.ac.auckland.concert.service.services;

import javax.persistence.EntityManager;
import javax.ws.rs.ApplicationPath;
import javax.ws.rs.core.Application;
import java.util.HashSet;
import java.util.Set;

@ApplicationPath("/services")
public class ConcertApplication extends Application {

    private Set<Object> _singletons = new HashSet<>();
    private Set<Class<?>> _classes = new HashSet<>();

    public ConcertApplication() {
        EntityManager em = PersistenceManager.instance().createEntityManager();
        try {
            // Clear database of any previous data from testing
            em.getTransaction().begin();

            // Delete all existing entities in conflict with multiple tests
            em.createQuery("DELETE FROM Token").executeUpdate();
            em.createQuery("DELETE FROM Booking").executeUpdate();
            em.createQuery("DELETE FROM User").executeUpdate();
            em.createQuery("DELETE FROM CreditCard").executeUpdate();
            em.createQuery("DELETE FROM SeatReservation").executeUpdate();
            em.createQuery("DELETE FROM Reservation").executeUpdate();
            em.getTransaction().commit();
        } finally {
            em.close();
        }

        _singletons.add(PersistenceManager.instance());
        _singletons.add(SubscriptionManager.instance());
        _classes.add(ConcertResource.class);
        _classes.add(PerformerResource.class);
        _classes.add(UserResource.class);
        _classes.add(ReserveResource.class);
    }

    @Override
    public Set<Object> getSingletons() {
        return _singletons;
    }

    @Override
    public Set<Class<?>> getClasses() {
        return _classes;
    }


}
