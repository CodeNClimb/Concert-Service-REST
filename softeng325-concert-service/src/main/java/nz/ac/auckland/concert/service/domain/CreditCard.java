package nz.ac.auckland.concert.service.domain;

import nz.ac.auckland.concert.common.dto.CreditCardDTO;
import nz.ac.auckland.concert.service.domain.jpa.LocalDateConverter;

import javax.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "CREDIT_CARDS")
public class CreditCard {

    public CreditCard() {}

    public CreditCard(CreditCardDTO.Type type, String name, String number, LocalDate expiryDate) {
        this.type = type;
        this.name = name;
        this.number = number;
        this.expiryDate = expiryDate;
    }


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Column(name = "TYPE")
    @Enumerated(EnumType.STRING)
    private CreditCardDTO.Type type;

    @Column(name = "NAME")
    private String name;

    @Column(name = "NUMBER")
    private String number;

    @Column(name = "EXPIRY_DATE")
    @Convert(converter = LocalDateConverter.class)
    private LocalDate expiryDate;


    public long getId() {
        return id;
    }

    public CreditCardDTO.Type getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    public String getNumber() {
        return number;
    }

    public LocalDate getExpiryDate() {
        return expiryDate;
    }
}
