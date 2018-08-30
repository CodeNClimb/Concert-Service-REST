package nz.ac.auckland.concert.service.domain;

import javax.persistence.*;
import java.util.Set;

@Entity
@Table(name = "USERS")
public class User {

    public User() {}

    public User(String username, String password, String firstName, String lastName, CreditCard creditCard, Reservation reservation, Set<Booking> bookings) {
        this.username = username;
        this.password = password;
        this.firstName = firstName;
        this.lastName = lastName;
        this.creditCard = creditCard;
        this.reservation = reservation;
        this.bookings = bookings;
    }

    @Column(name = "FIRST_NAME")
    private String firstName;


    @Column(name = "LAST_NAME")
    private String lastName;


    @Id
    @Column(name = "USERNAME")
    private String username;


    @Column(name = "PASSWORD")
    private String password;

    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "CREDIT_CARD", unique = true)
    private CreditCard creditCard;

    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "RESERVATION", unique = true)
    private Reservation reservation;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<Booking> bookings;


    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public CreditCard getCreditCard() {
        return creditCard;
    }

    public void setCreditCard(CreditCard creditCard) {
        this.creditCard = creditCard;
    }

    public Reservation getReservation() {
        return reservation;
    }

    public void setReservation(Reservation reservation) {
        this.reservation = reservation;
    }

    public Set<Booking> getBookings() {
        return bookings;
    }
}
