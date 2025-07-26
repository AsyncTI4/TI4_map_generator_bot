package ti4.spring.validator;

import lombok.experimental.UtilityClass;
import ti4.map.persistence.GameManager;
import ti4.spring.exception.InvalidGameNameException;

@UtilityClass
public class GameNameValidator {

    public static void validate(String gameName) {
        if (!GameManager.isValid(gameName)) throw new InvalidGameNameException(gameName);
    }
}
