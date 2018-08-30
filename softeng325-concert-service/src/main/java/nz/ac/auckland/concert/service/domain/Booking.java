package nz.ac.auckland.concert.service.domain;

import javax.persistence.*;
import java.util.Set;

@Entity
@Table(name = "BOOKINGS")
public class Booking {

    public Booking() {}

    public Booking(Reservation reservation, User user) {
        this.reservation = reservation;
        this.user = user;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @OneToOne(cascade = CascadeType.REMOVE)
    @JoinColumn(name = "Reservation_ID")
    private Reservation reservation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "USER_ID")
    private User user;

    public Reservation getReservation() {
        return reservation;
    }

    public User getUser() {
        return user;
    }
}
