package ti4.ai.sardakk;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import lombok.experimental.UtilityClass;
import ti4.ai.eval.BoardView;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.FoWHelper;
import ti4.helpers.Units.UnitType;

@UtilityClass
public class CommanderReach {

    public static final String COMMANDER = "sardakkcommander";
    public static final String BUTTON_PREFIX = "sardakkcommander_";

    public interface Garrison {
        int toKeep(Tile tile, Planet planet);
    }

    public record Pull(String position, String planet, UnitType type) {}

    public static boolean unlocked(Game game, Player seat) {
        return game.playerHasLeaderUnlockedOrAlliance(seat, COMMANDER);
    }

    public static boolean isButton(String handlerId) {
        return handlerId.startsWith(BUTTON_PREFIX);
    }

    public static List<Pull> pulls(Game game, Player seat, Tile target, Garrison garrison) {
        List<Pull> pulls = new ArrayList<>();
        if (!unlocked(game, seat)) return pulls;
        for (String position : FoWHelper.getAdjacentTiles(game, target.getPosition(), seat, false, true, true)) {
            Tile source = game.getTileByPosition(position);
            if (source == null || source == target || blockedByToken(game, seat, source)) continue;
            for (Planet planet : source.getPlanetUnitHolders()) {
                Set<UnitType> spare = spareTypes(game, seat, source, planet, garrison);
                if (spare.contains(UnitType.Infantry)) {
                    pulls.add(new Pull(position, planet.getName(), UnitType.Infantry));
                } else if (spare.contains(UnitType.Mech)) {
                    pulls.add(new Pull(position, planet.getName(), UnitType.Mech));
                }
            }
        }
        pulls.sort(Comparator.comparing(Pull::position).thenComparing(Pull::planet));
        return pulls;
    }

    public static boolean canSpare(
            Game game, Player seat, Tile source, Planet planet, Garrison garrison, UnitType type) {
        return !blockedByToken(game, seat, source)
                && spareTypes(game, seat, source, planet, garrison).contains(type);
    }

    private static boolean blockedByToken(Game game, Player seat, Tile source) {
        return source.hasPlayerCC(seat) && !game.isDominusOrb();
    }

    private static Set<UnitType> spareTypes(Game game, Player seat, Tile source, Planet planet, Garrison garrison) {
        Set<UnitType> spare = EnumSet.noneOf(UnitType.class);
        int infantry = BoardView.count(planet, seat, UnitType.Infantry);
        int mechs = BoardView.count(planet, seat, UnitType.Mech);
        int present = infantry + mechs;
        if (present == 0
                || BoardView.hasEnemyShips(game, seat, source)
                || BoardView.enemyGroundForcesOn(game, seat, planet)) {
            return spare;
        }
        int staying = Math.min(present, garrison.toKeep(source, planet));
        int mechsStaying = Math.min(mechs, staying);
        int infantryStaying = staying - mechsStaying;
        if (infantry - infantryStaying > 0) spare.add(UnitType.Infantry);
        if (mechs - mechsStaying > 0) spare.add(UnitType.Mech);
        return spare;
    }
}
