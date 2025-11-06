package ti4.spring.security;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.FORBIDDEN)
public class UserNotInGameForbiddenException extends RuntimeException {

    public UserNotInGameForbiddenException(String gameName, String userId) {
        super("Unable to find user with id '" + userId + "' in game '" + gameName + "'");
    }
}
