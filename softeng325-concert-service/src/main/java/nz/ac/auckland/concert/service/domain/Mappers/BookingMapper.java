package nz.ac.auckland.concert.service.domain.Mappers;

import nz.ac.auckland.concert.common.dto.BookingDTO;
import nz.ac.auckland.concert.service.domain.Booking;
import nz.ac.auckland.concert.service.domain.Reservation;

import java.util.stream.Collectors;

public class BookingMapper {

    public static BookingDTO toDto(Booking booking) {
        Reservation reservation = booking.getReservation();

        return new BookingDTO(
                reservation.getConcert().getId(),
                reservation.getConcert().getTitle(),
                reservation.getDate(),
                reservation.getSeats().stream().map(SeatMapper::toDto).collect(Collectors.toSet()),
                reservation.getPriceBand()
        );
    }

}
