package nz.ac.auckland.concert.service.domain;

import nz.ac.auckland.concert.service.domain.jpa.LocalDateTimeConverter;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "AUTHORIZATION_TOKENS")
public class Token {

    public Token() {}

    public Token(User user, String token, LocalDateTime timestamp) {
        this.user = user;
        this.token = token;
        this.expiry = timestamp;
    }

    @Id
    @Column(name = "USER_USERNAME")
    private String id;

    @MapsId // Maps one-to-one with a shared id of user's username. AUTHORIZATION_TOKENS also has username as its id.
    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "USER_USERNAME")
    private User user;

    @Column(name = "TOKEN")
    private String token;

    @Column(name = "EXPIRY")
    @Convert(converter = LocalDateTimeConverter.class)
    private LocalDateTime expiry;


    public User getUser() {
        return user;
    }

    public String getToken() {
        return token;
    }

    public LocalDateTime getExpiry() {
        return expiry;
    }
}
