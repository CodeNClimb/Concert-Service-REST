package nz.ac.auckland.concert.service.domain;


import nz.ac.auckland.concert.service.domain.Types.SubscriptionType;

import javax.persistence.*;

@Entity
@Table(name = "SUBSCRIPTIONS")
public class Subscription {

    public Subscription () {}

    public Subscription(SubscriptionType subscriptionType, User user) {
        this.subscriptionType = subscriptionType;
        this.user = user;
    }


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Column(name = "SUBSCRIPTION_TYPE")
    private SubscriptionType subscriptionType;

    @MapsId // Maps one-to-one with a shared id of user's username. AUTHORIZATION_TOKENS also has username as its id.
    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "USER_ID")
    private User user;


    public long getId() {
        return id;
    }

    public SubscriptionType getSubscriptionType() {
        return subscriptionType;
    }

    public User getUser() {
        return user;
    }
}
