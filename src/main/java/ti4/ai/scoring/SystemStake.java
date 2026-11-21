package ti4.ai.scoring;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import ti4.ai.eval.BoardView;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;

@UtilityClass
public class SystemStake {

    public static double points(Game game, Player seat) {
        Tile tile = game.getActiveSystem().isBlank() ? null : game.getTileByPosition(game.getActiveSystem());
        if (tile == null) return 0;
        ObjectiveValue objectives = new ObjectiveValue(game, seat);
        Footprint before = objectives.before();
        double lost = -objectives.gain(before.withoutShipsIn(tile.getPosition()));
        List<String> invaded = new ArrayList<>();
        for (Planet planet : tile.getPlanetUnitHolders()) {
            if (seat.getPlanets().contains(planet.getName())) {
                lost += -objectives.gain(before.withoutPlanet(planet.getName()));
            } else if (BoardView.groundForces(planet, seat) > 0) {
                invaded.add(planet.getName());
            }
        }
        double gained = objectives.gain(before.after(tile.getPosition(), List.of(), invaded));
        double worth = Math.max(lost, gained) / ObjectiveValue.VICTORY_POINT_VALUE;
        return Math.floor(worth + 1e-9);
    }
}
