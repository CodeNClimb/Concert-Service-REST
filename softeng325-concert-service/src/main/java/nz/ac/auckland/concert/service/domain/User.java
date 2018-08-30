package nz.ac.auckland.concert.service.domain;

import javax.persistence.*;

@Entity
@Table(name = "USERS")
public class User {

    public User() {}

    public User(String username, String password, String firstName, String lastName, CreditCard creditCard) {
        this.username = username;
        this.password = password;
        this.firstName = firstName;
        this.lastName = lastName;
        this.creditCard = creditCard;
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
}
