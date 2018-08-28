package nz.ac.auckland.concert.service.domain;

import nz.ac.auckland.concert.service.domain.jpa.LocalDateTimeConverter;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.Date;

@Entity
@Table(name = "AUTHORIZATION_TOKENS")
public class Token {

    @Id
    @Column(name = "USER_USERNAME")
    private String id;

    @MapsId // Maps one-to-one with a shared id of user's username. AUTHORIZATION_TOKENS also has username as its id.
    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "USER_USERNAME")
    private User user;

    @Column(name = "TOKEN")
    private String token;

    @Basic(optional = false)
    @Column(name = "TIMESTAMP", insertable = false, updatable = false)
    @Temporal(TemporalType.TIMESTAMP)
    @Convert(converter = LocalDateTimeConverter.class)
    private Date timeStamp;


    public User getUser() {
        return user;
    }

    public String getToken() {
        return token;
    }

    public Date getTimeStamp() {
        return timeStamp;
    }
}
