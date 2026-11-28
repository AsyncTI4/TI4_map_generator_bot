package ti4.ai.strategy;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.FoWHelper;

@UtilityClass
class WarfareIdle {

    static String announcement(Game game, Player seat) {
        String text = "🤖 " + seat.getRepresentationNoPing()
                + " resolves **Warfare** without a tactical action: no system is worth activating";
        List<String> reasons = reasons(game, seat);
        return reasons.isEmpty() ? text + "." : text + ", as " + String.join(" and ", reasons) + ".";
    }

    private static List<String> reasons(Game game, Player seat) {
        List<String> reasons = new ArrayList<>();
        if (shipsAllLocked(game, seat)) {
            reasons.add("all of its ships sit in systems that already hold its command tokens");
        }
        if (seat.getReadiedPlanets().isEmpty() && seat.getTg() == 0) {
            reasons.add("it has nothing left to spend on production");
        }
        return reasons;
    }

    private static boolean shipsAllLocked(Game game, Player seat) {
        List<Tile> withShips = game.getTileMap().values().stream()
                .filter(tile -> FoWHelper.playerHasActualShipsInSystem(seat, tile))
                .toList();
        return !withShips.isEmpty() && withShips.stream().allMatch(tile -> tile.hasPlayerCC(seat));
    }
}
