package nz.ac.auckland.concert.service.domain.Mappers;

import nz.ac.auckland.concert.common.dto.PerformerDTO;
import nz.ac.auckland.concert.service.domain.Performer;

public class PerformerMapper {

    public static Performer toDomainModel(PerformerDTO performerDto) {
        return new Performer();
    }

    public static PerformerDTO toDto(Performer performer) {
        return new PerformerDTO(
                performer.getId(),
                performer.getName(),
                performer.getImageName(),
                performer.getGenre(),
                performer.getConcertIds()
        );
    }

}
