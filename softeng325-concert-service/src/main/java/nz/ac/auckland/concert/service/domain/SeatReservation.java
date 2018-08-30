package nz.ac.auckland.concert.service.domain;

import nz.ac.auckland.concert.common.types.SeatNumber;
import nz.ac.auckland.concert.common.types.SeatRow;
import nz.ac.auckland.concert.service.domain.jpa.SeatNumberConverter;

import javax.persistence.*;

@Entity
@Table(name = "SEAT_RESERVATIONS")
public class SeatReservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Column(name = "SEAT_ROW")
    private SeatRow row;

    @Column(name = "SEAT_NUMBER")
    @Convert(converter = SeatNumberConverter.class)
    private SeatNumber number;

    public SeatReservation() {}

    public SeatReservation(SeatRow row, SeatNumber number) {
        this.row = row;
        this.number = number;
    }

    public SeatRow getRow() {
        return row;
    }

    public SeatNumber getNumber() {
        return number;
    }

}
