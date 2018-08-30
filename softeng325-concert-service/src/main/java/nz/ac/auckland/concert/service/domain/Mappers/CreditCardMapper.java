package nz.ac.auckland.concert.service.domain.Mappers;

import nz.ac.auckland.concert.common.dto.CreditCardDTO;
import nz.ac.auckland.concert.service.domain.CreditCard;

public class CreditCardMapper {

    public static CreditCard toDomain(CreditCardDTO creditCardDTO) {
        return new CreditCard(
                creditCardDTO.getType(),
                creditCardDTO.getName(),
                creditCardDTO.getNumber(),
                creditCardDTO.getExpiryDate()
        );
    }

    public static CreditCardDTO toDto(CreditCard creditCard) {
        return new CreditCardDTO(
                creditCard.getType(),
                creditCard.getName(),
                creditCard.getNumber(),
                creditCard.getExpiryDate()
        );
    }

}
