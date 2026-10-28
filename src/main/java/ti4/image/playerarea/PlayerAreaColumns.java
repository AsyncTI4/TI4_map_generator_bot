package ti4.image.playerarea;

import java.util.List;
import ti4.game.Game;

public final class PlayerAreaColumns {

    static final int MEGA_GAME_MIN_PLAYERS = 9;
    public static final int COLUMN_GAP = 10;

    private PlayerAreaColumns() {}

    public static boolean isMegaGame(Game game) {
        return game.getRealPlayers().size() >= MEGA_GAME_MIN_PLAYERS;
    }

    public static <T> List<List<T>> split(Game game, List<T> playerAreas) {
        if (!isMegaGame(game)) {
            return List.of(playerAreas);
        }
        int leftColumnSize = (playerAreas.size() + 1) / 2;
        return List.of(playerAreas.subList(0, leftColumnSize), playerAreas.subList(leftColumnSize, playerAreas.size()));
    }

    public static int columnWidth(int totalWidth, int left, int columnCount) {
        return (totalWidth - left - COLUMN_GAP * (columnCount - 1)) / columnCount;
    }
}
