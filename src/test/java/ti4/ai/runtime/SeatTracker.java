package ti4.ai.runtime;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import ti4.ai.eval.BoardView;
import ti4.ai.tactical.TacticalPlanner;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Units.UnitType;

/**
 * Per Occupy the Seat of the Empire holder at the start of each status phase: whether it controls Mecatol Rex, the
 * ships it has there by type, whether it scored, and whether an enemy space cannon covers Mecatol while the holder
 * controls it with fewer than three ships (the case in which the planner used to refuse the move that adds ships).
 */
final class SeatTracker {

    private static final String OCCUPY_THE_SEAT = "ose";
    private static final int SHIPS_NEEDED = 3;

    private record Holding(
            String faction,
            int round,
            boolean controls,
            Map<UnitType, Integer> ships,
            boolean covered,
            boolean scored,
            int others,
            String capture) {}

    private final List<Holding> holdings = new ArrayList<>();

    synchronized void record(Game game) {
        Tile mecatol = game.getMecatolTile();
        if (mecatol == null) return;
        for (Player seat : game.getRealPlayers()) {
            boolean scored = seat.getSecretsScored().containsKey(OCCUPY_THE_SEAT);
            if (!scored && !seat.getSecretsUnscored().containsKey(OCCUPY_THE_SEAT)) continue;
            boolean controls = seat.getPlanets().stream().anyMatch(game.mecatols()::contains);
            Map<UnitType, Integer> ships = new EnumMap<>(BoardView.ships(BoardView.space(mecatol), seat));
            int total = ships.values().stream().mapToInt(Integer::intValue).sum();
            boolean covered = controls
                    && total < SHIPS_NEEDED
                    && ButtonHelper.getPlayersWithPds2Cover(seat, game, mecatol.getPosition()).stream()
                            .anyMatch(player -> player != seat);
            holdings.add(new Holding(
                    seat.getFaction(),
                    game.getRound(),
                    controls,
                    ships,
                    covered,
                    scored,
                    othersAt(game, seat, mecatol),
                    controls || scored ? "-" : bestPlan(game, seat, mecatol)));
        }
    }

    private static int othersAt(Game game, Player seat, Tile mecatol) {
        return (int) game.getRealPlayers().stream()
                .filter(other -> other != seat)
                .filter(other -> mecatol.containsPlayersUnits(other)
                        || other.getPlanets().stream().anyMatch(game.mecatols()::contains))
                .count();
    }

    private static String bestPlan(Game game, Player seat, Tile mecatol) {
        return TacticalPlanner.forTarget(game, seat, mecatol.getPosition())
                .map(plan -> plan.kind().name().toLowerCase() + String.format(" %.1f", plan.score()))
                .orElse("none");
    }

    synchronized List<String> lines(Game game) {
        if (holdings.isEmpty()) return List.of();
        List<String> lines = new ArrayList<>();
        for (Holding holding : holdings) {
            lines.add(String.format(
                    "OccupyTheSeat %s %s r%d: controls Mecatol %s, ships %s, cannon-blocked %s, scored %s, others at Mecatol %d, best plan on Mecatol %s",
                    game.getName(),
                    holding.faction(),
                    holding.round(),
                    holding.controls(),
                    holding.ships(),
                    holding.covered(),
                    holding.scored(),
                    holding.others(),
                    holding.capture()));
        }
        long blocked = holdings.stream().filter(Holding::covered).count();
        lines.add(String.format(
                "OccupyTheSeat %s: holder-phases cannon-blocked %d of %d", game.getName(), blocked, holdings.size()));
        return lines;
    }
}
