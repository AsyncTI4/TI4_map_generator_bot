package ti4.ai.tactical;

import java.util.Map;
import java.util.TreeMap;
import ti4.ai.eval.BoardView;
import ti4.ai.eval.FracturePrize;
import ti4.ai.eval.MovementGraph;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;

/**
 * Self-play fleet log (-Dai.selfplay.fleetlog=true): at the start of each round, one line per seat with its units on
 * the board by type, its space docks and PDS with each dock's distance to the nearest ingress, and its ships at an
 * ingress and inside the Fracture, to compare the AI's building and movement with a human game.
 */
public final class FleetLog {

    public static final String PROPERTY = "ai.selfplay.fleetlog";
    private static final int INGRESS_SEARCH = 4;

    private FleetLog() {}

    public static boolean enabled() {
        return Boolean.getBoolean(PROPERTY);
    }

    public static String line(Game game, Player seat) {
        Map<String, Integer> units = new TreeMap<>();
        StringBuilder docks = new StringBuilder();
        int pds = 0;
        int atIngress = 0;
        int inFracture = 0;
        for (Tile tile : game.getTileMap().values()) {
            for (UnitType type : UnitType.values()) {
                int count = tile.getUnitHolders().values().stream()
                        .mapToInt(holder -> BoardView.count(holder, seat, type))
                        .sum();
                if (count > 0) units.merge(type.getValue(), count, Integer::sum);
            }
            int ships = BoardView.nonFighterShips(BoardView.space(tile), seat);
            if (FracturePrize.hasIngress(tile)) atIngress += ships;
            if (tile.isFracture()) inFracture += ships;
            for (Planet planet : tile.getPlanetUnitHolders()) {
                pds += BoardView.count(planet, seat, UnitType.Pds);
                if (BoardView.count(planet, seat, UnitType.Spacedock) > 0) {
                    docks.append(tile.getPosition())
                            .append('(')
                            .append(ingressDistance(game, seat, tile))
                            .append(')');
                }
            }
        }
        return "FLEETLOG game=" + game.getName() + " r=" + game.getRound() + " seat=" + seat.getFaction() + " vp="
                + seat.getTotalVictoryPoints() + " fleetCC=" + seat.getFleetCC() + " units=" + units + " docks="
                + docks + " pds=" + pds + " atIngress=" + atIngress + " inFracture=" + inFracture + " techs="
                + seat.getTechs().stream()
                        .filter(tech -> tech.matches("cv2|dn2|gd|cr2|dd2|ws|fl|amd"))
                        .toList()
                + " march="
                + FractureMarch.of(game, seat).map(FractureMarch::position).orElse("-");
    }

    private static String ingressDistance(Game game, Player seat, Tile tile) {
        return MovementGraph.reach(game, seat, tile.getPosition(), INGRESS_SEARCH).entrySet().stream()
                .filter(entry -> {
                    Tile reached = game.getTileByPosition(entry.getKey());
                    return reached != null && FracturePrize.hasIngress(reached);
                })
                .mapToInt(Map.Entry::getValue)
                .min()
                .stream()
                .mapToObj(String::valueOf)
                .findFirst()
                .orElse("-");
    }
}
