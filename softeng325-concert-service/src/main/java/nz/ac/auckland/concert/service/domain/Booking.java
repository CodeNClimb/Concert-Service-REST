package nz.ac.auckland.concert.service.domain;

import javax.persistence.*;

@Entity
@Table(name = "BOOKINGS")
public class Booking {

    public Booking() {}

    public Booking(Reservation reservation) {
        this.reservation = reservation;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @OneToOne(cascade = CascadeType.REMOVE)
    @JoinColumn(name = "Reservation_ID")
    private Reservation reservation;

    public Reservation getReservation() {
        return reservation;
    }
}
