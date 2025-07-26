package ti4.spring.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidUserIdException extends RuntimeException {

    public InvalidUserIdException(String gameName, String userId) {
        super("Unable to find user with id '" + userId + "' in game '" + gameName + "'.");
    }
}
