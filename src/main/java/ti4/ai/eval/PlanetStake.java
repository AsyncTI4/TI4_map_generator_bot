package ti4.ai.eval;

import java.util.Map;
import lombok.experimental.UtilityClass;
import ti4.ai.scoring.ObjectiveValue;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.helpers.Units.UnitType;

@UtilityClass
public class PlanetStake {

    public static final double SPACE_DOCK = 4.0;
    public static final double PDS = 3.0;
    private static final Map<UnitType, Double> STRUCTURE_VALUES =
            Map.of(UnitType.Spacedock, SPACE_DOCK, UnitType.Pds, PDS);

    public static double of(Game game, Player seat, Planet planet) {
        String name = planet.getName();
        double stake = BoardView.planetValue(planet);
        if (!seat.getExhaustedPlanets().contains(name)) {
            stake += Math.max(BoardView.planetResources(game, name), BoardView.planetInfluence(game, name));
        }
        ObjectiveValue objectives = new ObjectiveValue(game, seat);
        return stake + Math.max(0, -objectives.gain(objectives.before().withoutPlanet(name)));
    }

    public static double structures(Planet planet, Player seat) {
        return STRUCTURE_VALUES.entrySet().stream()
                .mapToDouble(entry -> BoardView.count(planet, seat, entry.getKey()) * entry.getValue())
                .sum();
    }
}
