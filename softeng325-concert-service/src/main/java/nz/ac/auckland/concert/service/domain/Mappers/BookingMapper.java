package nz.ac.auckland.concert.service.domain.Mappers;

import nz.ac.auckland.concert.common.dto.BookingDTO;
import nz.ac.auckland.concert.service.domain.Booking;
import nz.ac.auckland.concert.service.domain.Reservation;

import java.util.stream.Collectors;

/**
 * Mapper for mapping domain booking object to BookingDTO object. Note there is no method for the]
 * directing on vice-versa because this function is not needed anywhere in the system as no BookingDTO
 * objects are sent to the service.
 */
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
