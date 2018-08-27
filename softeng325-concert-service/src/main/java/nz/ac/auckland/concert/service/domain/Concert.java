package nz.ac.auckland.concert.service.domain;

import nz.ac.auckland.concert.common.types.PriceBand;
import nz.ac.auckland.concert.service.domain.jpa.LocalDateTimeConverter;

import javax.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

@Entity
@Table(name = "CONCERTS")
public class Concert {

    public Concert() {}

    @Id
    @GeneratedValue
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

    @ManyToMany(cascade = CascadeType.PERSIST)
    @JoinTable(
            name = "CONCERT_PERFORMER",
            joinColumns = @JoinColumn(name = "CONCERT_ID"),
            inverseJoinColumns = @JoinColumn(name = "PERFORMER_ID", nullable = false)
    )
    private Set<Performer> performers;



    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public void setPerformers(Set<Performer> performers) {
        this.performers = performers;
    }

    public Set<LocalDateTime> getDates() {
        return dates;
    }

    public void setDates(Set<LocalDateTime> dates) {
        this.dates = dates;
    }

    public Map<PriceBand, BigDecimal> getPrices() {
        return prices;
    }

    public void setPrices(Map<PriceBand, BigDecimal> prices) {
        this.prices = prices;
    }
}
