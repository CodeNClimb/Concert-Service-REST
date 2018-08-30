package nz.ac.auckland.concert.service.domain.Mappers;

import nz.ac.auckland.concert.common.dto.UserDTO;
import nz.ac.auckland.concert.service.domain.User;

public class UserMapper {

    public static UserDTO toDTO(User userDomain) {
        return new UserDTO(
                userDomain.getUsername(),
                userDomain.getPassword(),
                userDomain.getLastName(),
                userDomain.getFirstName()
        );
    }

    public static User toDomain(UserDTO userDto) {
        return new User(
                userDto.getUsername(),
                userDto.getPassword(),
                userDto.getFirstname(),
                userDto.getLastname(),
                null, // No credit card as of yet :)
                null, // No reservations as of yet :)
                null // No bookings as of yet :)
        );
    }

}
