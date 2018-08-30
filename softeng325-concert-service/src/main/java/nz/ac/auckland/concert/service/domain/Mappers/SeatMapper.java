package nz.ac.auckland.concert.service.domain.Mappers;

import nz.ac.auckland.concert.common.dto.SeatDTO;
import nz.ac.auckland.concert.service.domain.SeatReservation;

public class SeatMapper {

    public static SeatDTO toDto(SeatReservation reservation) {
        return new SeatDTO(
                reservation.getRow(),
                reservation.getNumber()
        );
    }

    public static SeatReservation toReservation(SeatDTO dto) {
        return new SeatReservation(
                dto.getRow(),
                dto.getNumber()
        );
    }

}
