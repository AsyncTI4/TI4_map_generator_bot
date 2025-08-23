package ti4.spring.context;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidGameNameException extends RuntimeException {

    public InvalidGameNameException(String gameName) {
        super("Unable to find game '" + gameName + "'");
    }
}
