package ti4.spring.validator;

import lombok.experimental.UtilityClass;
import ti4.map.persistence.GameManager;
import ti4.spring.exception.InvalidUserIdException;

@UtilityClass
public class UserIdValidator {

    public static void validate(String gameName, String userId) {
        boolean playerNotInGame = GameManager.getManagedGame(gameName).getPlayers().stream()
            .noneMatch(player -> userId.equals(player.getId()));
        if (playerNotInGame) throw new InvalidUserIdException(gameName, userId);
    }
}
