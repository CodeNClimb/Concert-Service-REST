package nz.ac.auckland.concert.service.domain;

import nz.ac.auckland.concert.common.types.PriceBand;
import nz.ac.auckland.concert.service.domain.jpa.LocalDateTimeConverter;

import javax.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Entity
@Table(name = "CONCERTS")
public class Concert {

    public Concert() {} // Default constructor required for JPA

    public Concert(long id, String title) {
        this.id = id;
        this.title = title;
    }


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private long id;


    @Column(name = "TITLE")
    private String title;


    @ElementCollection
    @CollectionTable(name = "CONCERT_DATES")
    @Convert(converter = LocalDateTimeConverter.class)
    private Set<LocalDateTime> dates;


    @ElementCollection
    @CollectionTable(name = "CONCERT_TARIFS", joinColumns = @JoinColumn(name = "CONCERT_ID"))
    @MapKeyColumn(name = "PRICE_BAND")
    @Column(name = "PRICE")
    @MapKeyClass(PriceBand.class)
    @MapKeyEnumerated(EnumType.STRING)
    private Map<PriceBand, BigDecimal> prices;


    @ManyToMany(cascade = CascadeType.PERSIST, fetch = FetchType.LAZY)
    @JoinTable(
            name = "CONCERT_PERFORMER",
            joinColumns = @JoinColumn(name = "CONCERT_ID"),
            inverseJoinColumns = @JoinColumn(name = "PERFORMER_ID", nullable = false)
    )
    private Set<Performer> performers;


    public long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public Set<LocalDateTime> getDates() {
        return dates;
    }

    public Map<PriceBand, BigDecimal> getPrices() {
        return prices;
    }

    public Set<Performer> getPerformers() {
        return performers;
    }

    public Set<Long> getPerformerIds() {
        return performers.stream().map(Performer::getId).collect(Collectors.toSet());
    }
}
