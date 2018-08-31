package nz.ac.auckland.concert.service.domain.Mappers;

import nz.ac.auckland.concert.common.dto.PerformerDTO;
import nz.ac.auckland.concert.service.domain.Performer;

import java.util.HashSet;

public class PerformerMapper {

    public static Performer toDomainModel(PerformerDTO performerDto) {
        return new Performer(
                performerDto.getName(),
                performerDto.getImageName(),
                null, // Creating a new performer has no need for creating their concerts or genre at same time.
                null
        );
    }

    public static PerformerDTO toDto(Performer performer) {
        return new PerformerDTO(
                performer.getId(),
                performer.getName(),
                performer.getImageName(),
                performer.getGenre(),
                performer.getConcertIds() == null ? new HashSet<>() : performer.getConcertIds()
        );
    }

}
