package nz.ac.auckland.concert.service.domain.Mappers;

import nz.ac.auckland.concert.common.dto.CreditCardDTO;
import nz.ac.auckland.concert.service.domain.CreditCard;

/**
 * Mapper for mapping domain CreditCardDTO object to CreditCard object. Note there is no method for the
 * directing in vice-versa because this function is not needed anywhere in the system as no credit card details
 * are sent by the service to any client.
 */
public class CreditCardMapper {

    public static CreditCard toDomain(CreditCardDTO creditCardDTO) {
        return new CreditCard(
                creditCardDTO.getType(),
                creditCardDTO.getName(),
                creditCardDTO.getNumber(),
                creditCardDTO.getExpiryDate()
        );
    }
}
