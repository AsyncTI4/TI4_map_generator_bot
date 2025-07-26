package ti4.controller.validator;

import ti4.controller.exception.InvalidGameNameException;
import ti4.map.persistence.GameManager;

public class GameNameValidator {

    public static void validate(String gameName) {
        if (!GameManager.isValid(gameName)) throw new InvalidGameNameException(gameName);
    }
}
