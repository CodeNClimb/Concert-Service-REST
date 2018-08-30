package nz.ac.auckland.concert.service.domain;

import nz.ac.auckland.concert.common.types.PriceBand;
import nz.ac.auckland.concert.service.domain.jpa.LocalDateTimeConverter;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.Set;

@Entity
@Embeddable
@Table(name = "RESERVATIONS")
public class Reservation {

    public Reservation() {}

    public Reservation(Set<SeatReservation> seats, Concert concert, LocalDateTime date, LocalDateTime expiry, PriceBand priceBand) {
        this.seats = seats;
        this.concert = concert;
        this.date = date;
        this.expiry = expiry;
        this.priceBand = priceBand;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private long id;

    @OneToMany(cascade = CascadeType.ALL)
    @JoinColumn(name = "SEAT_RESERVATION_ID")
    private Set<SeatReservation> seats;

    @ManyToOne
    @JoinColumn(name = "CONCERT_ID")
    private Concert concert;

    @Column(name = "CONCERT_DATE")
    @Convert(converter = LocalDateTimeConverter.class)
    private LocalDateTime date;

    @Column(name = "EXPIRY")
    @Convert(converter = LocalDateTimeConverter.class)
    private LocalDateTime expiry;

    @Column(name = "PRICE_BAND")
    @Enumerated(EnumType.STRING)
    private PriceBand priceBand;

    public long getId() {
        return id;
    }

    public Set<SeatReservation> getSeats() {
        return seats;
    }

    public Concert getConcert() {
        return concert;
    }

    public LocalDateTime getDate() {
        return date;
    }

    public LocalDateTime getExpiry() {
        return expiry;
    }

    public PriceBand getPriceBand() {
        return priceBand;
    }
}
