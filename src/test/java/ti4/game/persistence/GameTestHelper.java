package ti4.game.persistence;

import lombok.experimental.UtilityClass;
import ti4.game.Game;

@UtilityClass
public class GameTestHelper {
    private static final String GAME_NAME = "pbd15036";

    static Game loadGame() {
        return GameLoadService.load(GAME_NAME);
    }

    public static Game loadGame(String gameName) {
        return GameLoadService.load(gameName);
    }
}
