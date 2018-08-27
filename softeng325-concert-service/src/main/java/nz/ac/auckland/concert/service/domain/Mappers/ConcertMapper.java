package nz.ac.auckland.concert.service.domain.Mappers;

import nz.ac.auckland.concert.common.dto.ConcertDTO;
import nz.ac.auckland.concert.service.domain.Concert;

public class ConcertMapper {

    public static Concert toDomainModel(ConcertDTO concertDto) {
        return new Concert(
                concertDto.getId(),
                concertDto.getTitle()
        );
    }

    public static ConcertDTO doDto(Concert concert) {

        return new ConcertDTO(
                concert.getId(),
                concert.getTitle(),
                concert.getDates(),
                concert.getPrices(),
                concert.getPerformerIds()
        );
    }

}
