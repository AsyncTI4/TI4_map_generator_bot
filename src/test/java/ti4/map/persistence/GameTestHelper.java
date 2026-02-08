package ti4.map.persistence;

import lombok.experimental.UtilityClass;
import ti4.map.Game;

@UtilityClass
public class GameTestHelper {
    private static final String GAME_NAME = "pbd15036";

    static Game loadGame() {
        return GameLoadService.load(GAME_NAME);
    }
}
