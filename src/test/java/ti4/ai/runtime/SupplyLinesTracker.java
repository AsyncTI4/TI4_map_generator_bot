package ti4.ai.runtime;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import ti4.ai.eval.BoardView;
import ti4.ai.tactical.SupplyLines;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;

/**
 * Per Cut Supply Lines holder at the start of each status phase: the other players' dock systems and why each one
 * could not be used (their ships, an enemy space cannon, or out of reach), whether a ship was parked in one, and
 * whether the holder scored. Per game: ships that arrived in a cannon-covered dock system and how many of the holder's
 * ships were gone from one on the next look (a move away also counts, so this is an upper bound on cannon losses).
 */
final class SupplyLinesTracker {

    private static final String CUT_SUPPLY_LINES = "csl";

    private static final class Holding {
        final String faction;
        final int round;
        final Map<String, Integer> blocked;
        final boolean parked;
        boolean scored;

        Holding(String faction, int round, Map<String, Integer> blocked, boolean parked) {
            this.faction = faction;
            this.round = round;
            this.blocked = blocked;
            this.parked = parked;
        }
    }

    private final List<Holding> holdings = new ArrayList<>();
    private final Map<String, Map<String, Integer>> coveredShips = new HashMap<>();
    private int arrivedUnderCannon;
    private int goneFromCannon;

    synchronized void observe(Game game) {
        for (Player seat : game.getRealPlayers()) {
            boolean holds = seat.getSecretsUnscored().containsKey(CUT_SUPPLY_LINES);
            if (seat.getSecretsScored().containsKey(CUT_SUPPLY_LINES)) markScored(seat);
            if (holds) observeCoveredDocks(game, seat);
        }
    }

    synchronized void record(Game game) {
        for (Player seat : game.getRealPlayers()) {
            if (!seat.getSecretsUnscored().containsKey(CUT_SUPPLY_LINES)) continue;
            Set<Tile> inReach = Set.copyOf(SupplyLines.dockTilesInReach(game, seat));
            Map<String, Integer> blocked = new LinkedHashMap<>();
            boolean parked = false;
            for (Tile tile : game.getTileMap().values()) {
                if (!hasOtherDock(game, seat, tile)) continue;
                if (BoardView.hasOwnShips(seat, tile)) {
                    parked = true;
                    blocked.merge("parked", 1, Integer::sum);
                } else {
                    blocked.merge(reason(game, seat, tile, inReach), 1, Integer::sum);
                }
            }
            holdings.add(new Holding(seat.getFaction(), game.getRound(), blocked, parked));
        }
    }

    synchronized List<String> lines(Game game) {
        if (holdings.isEmpty()) return List.of();
        List<String> lines = new ArrayList<>();
        for (Holding holding : holdings) {
            lines.add(String.format(
                    "CutSupplyLines %s %s r%d: dock systems %s, parked at status %s, scored %s",
                    game.getName(), holding.faction, holding.round, holding.blocked, holding.parked, holding.scored));
        }
        long parked = holdings.stream().filter(holding -> holding.parked).count();
        lines.add(String.format(
                "CutSupplyLines %s: holder-phases parked %d of %d, arrived under cannon %d, ships gone from covered"
                        + " docks %d",
                game.getName(), parked, holdings.size(), arrivedUnderCannon, goneFromCannon));
        return lines;
    }

    private void markScored(Player seat) {
        for (int index = holdings.size() - 1; index >= 0; index--) {
            Holding holding = holdings.get(index);
            if (holding.faction.equals(seat.getFaction())) {
                holding.scored = true;
                return;
            }
        }
    }

    private void observeCoveredDocks(Game game, Player seat) {
        Map<String, Integer> now = new HashMap<>();
        for (Tile tile : game.getTileMap().values()) {
            if (!hasOtherDock(game, seat, tile) || !covered(game, seat, tile)) continue;
            int ships = BoardView.ships(BoardView.space(tile), seat).values().stream()
                    .mapToInt(Integer::intValue)
                    .sum();
            if (ships > 0) now.put(tile.getPosition(), ships);
        }
        Map<String, Integer> before = coveredShips.getOrDefault(seat.getFaction(), Map.of());
        now.forEach((position, ships) -> {
            int earlier = before.getOrDefault(position, 0);
            if (ships > earlier) arrivedUnderCannon += ships - earlier;
        });
        before.forEach((position, ships) -> {
            int later = now.getOrDefault(position, 0);
            if (later < ships) goneFromCannon += ships - later;
        });
        coveredShips.put(seat.getFaction(), now);
    }

    private static String reason(Game game, Player seat, Tile tile, Set<Tile> inReach) {
        if (BoardView.hasEnemyShips(game, seat, tile)) return "ships";
        if (!inReach.contains(tile)) return "reach";
        return covered(game, seat, tile) ? "cannon" : "open";
    }

    private static boolean covered(Game game, Player seat, Tile tile) {
        return ButtonHelper.getPlayersWithPds2Cover(seat, game, tile.getPosition()).stream()
                .anyMatch(player -> player != seat);
    }

    private static boolean hasOtherDock(Game game, Player seat, Tile tile) {
        return tile.getUnitHolders().values().stream()
                .anyMatch(holder -> game.getRealPlayers().stream()
                        .anyMatch(other -> other != seat
                                && BoardView.count(holder, other, ti4.helpers.Units.UnitType.Spacedock) > 0));
    }
}
