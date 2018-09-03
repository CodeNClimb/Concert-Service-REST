package nz.ac.auckland.concert.service.domain.Mappers;

import nz.ac.auckland.concert.common.dto.ConcertDTO;
import nz.ac.auckland.concert.common.types.PriceBand;
import nz.ac.auckland.concert.service.domain.Concert;
import nz.ac.auckland.concert.service.domain.Performer;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Mapper class for mapping domain Concert objects to ConcertDTO objects and vice-versa.
 */
public class ConcertMapper {

    public static Concert toDomainModel(ConcertDTO concertDto) {
        return new Concert(
                concertDto.getId(),
                concertDto.getTitle(),
                concertDto.getDates(),
                Arrays.stream(PriceBand.values()).collect(Collectors.toMap(priceBand -> priceBand, concertDto::getTicketPrice, (a, b) -> b)),
                concertDto.getPerformerIds().stream().map(Performer::new).collect(Collectors.toSet())
        );
    }

    public static ConcertDTO toDto(Concert concert) {

        return new ConcertDTO(
                concert.getId(),
                concert.getTitle(),
                concert.getDates(),
                concert.getPrices(),
                concert.getPerformerIds()
        );
    }

}
