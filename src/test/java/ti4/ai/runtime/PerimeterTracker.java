package ti4.ai.runtime;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import ti4.ai.eval.BoardView;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Units.UnitType;

/**
 * Per Establish a Perimeter holder at the start of each status phase: the PDS on the board, whether it held Construction
 * or really followed it, the PDS and space docks that appeared on the board in the round (a dock placed by a follow
 * while the secret is held should be 0), and whether it scored. A follow counts only when the seat placed a structure
 * and spent a strategy token while the card was another seat's: a seat that merely declined or skipped is marked as
 * having followed too. A per-game line also counts the real Construction follows of every seat, by unit and by whether
 * the structure went to the home system or forward.
 */
final class PerimeterTracker {

    private static final String ESTABLISH_A_PERIMETER = "eap";
    private static final int CONSTRUCTION = 4;
    private static final String PRIMARY = "primary";
    private static final String FOLLOW = "follow";
    private static final String OTHER = "other";
    private static final String HOME = "home";
    private static final String FORWARD = "forward";

    private record Holding(
            String faction,
            int round,
            int pds,
            boolean primary,
            boolean followed,
            Map<String, Integer> placed,
            boolean scored) {}

    private record Counts(int pds, int docks, int homePds, int homeDocks, int strategyTokens) {}

    private final Map<String, Counts> before = new HashMap<>();
    private final Map<String, Map<String, Integer>> placedThisRound = new HashMap<>();
    private final Set<String> followedThisRound = new HashSet<>();
    private final Set<String> paidThisRound = new HashSet<>();
    private final Map<String, Integer> followsByPlacement = new TreeMap<>();
    private final List<Holding> holdings = new ArrayList<>();
    private int dockFollows;

    synchronized void observe(Game game) {
        for (Player seat : game.getRealPlayers()) {
            String faction = seat.getFaction();
            Counts now = counts(game, seat);
            Counts then = before.put(faction, now);
            if (then == null) continue;
            if (spentStrategyToken(game, seat, now, then)) paidThisRound.add(faction);
            String role = role(game, seat, paidThisRound.contains(faction));
            observeUnit(seat, "pds", role, now.pds() - then.pds(), now.homePds() - then.homePds());
            observeUnit(seat, "dock", role, now.docks() - then.docks(), now.homeDocks() - then.homeDocks());
        }
    }

    private void observeUnit(Player seat, String unit, String role, int added, int addedAtHome) {
        if (added <= 0) return;
        String faction = seat.getFaction();
        boolean holder = holds(seat);
        if (holder) place(faction, unit, role, added);
        if (!FOLLOW.equals(role)) return;
        followedThisRound.add(faction);
        int home = Math.max(0, Math.min(added, addedAtHome));
        followsByPlacement.merge(unit + "/" + HOME, home, Integer::sum);
        followsByPlacement.merge(unit + "/" + FORWARD, added - home, Integer::sum);
        if ("dock".equals(unit) && seat.getSecretsUnscored().containsKey(ESTABLISH_A_PERIMETER)) dockFollows += added;
    }

    synchronized void record(Game game) {
        for (Player seat : game.getRealPlayers()) {
            if (!holds(seat)) continue;
            String faction = seat.getFaction();
            boolean played = game.getPlayedSCs().contains(CONSTRUCTION);
            holdings.add(new Holding(
                    faction,
                    game.getRound(),
                    count(game, seat, "pds"),
                    played && seat.getSCs().contains(CONSTRUCTION),
                    played && followedThisRound.contains(faction),
                    Map.copyOf(placedThisRound.getOrDefault(faction, Map.of())),
                    seat.getSecretsScored().containsKey(ESTABLISH_A_PERIMETER)));
            placedThisRound.remove(faction);
        }
        followedThisRound.clear();
        paidThisRound.clear();
    }

    private static boolean spentStrategyToken(Game game, Player seat, Counts now, Counts then) {
        return game.getPlayedSCs().contains(CONSTRUCTION)
                && !seat.getSCs().contains(CONSTRUCTION)
                && seat.hasFollowedSC(CONSTRUCTION)
                && now.strategyTokens() < then.strategyTokens();
    }

    synchronized List<String> lines(Game game) {
        List<String> lines = new ArrayList<>();
        for (Holding holding : holdings) {
            lines.add(String.format(
                    "Perimeter %s %s r%d: pds %d, held Construction %s, followed %s, placed %s, scored %s",
                    game.getName(),
                    holding.faction(),
                    holding.round(),
                    holding.pds(),
                    holding.primary(),
                    holding.followed(),
                    holding.placed(),
                    holding.scored()));
        }
        if (!holdings.isEmpty()) {
            lines.add(String.format(
                    "Perimeter %s: docks placed by follows while held %d, holder-phases %d",
                    game.getName(), dockFollows, holdings.size()));
        }
        if (!followsByPlacement.isEmpty()) {
            lines.add(
                    String.format("Construction %s: real follows by placement %s", game.getName(), followsByPlacement));
        }
        return lines;
    }

    private void place(String faction, String type, String role, int count) {
        placedThisRound
                .computeIfAbsent(faction, ignored -> new HashMap<>())
                .merge(type + "/" + role, count, Integer::sum);
    }

    private static String role(Game game, Player seat, boolean spentStrategyToken) {
        if (!game.getPlayedSCs().contains(CONSTRUCTION)) return OTHER;
        if (seat.getSCs().contains(CONSTRUCTION)) return PRIMARY;
        return seat.hasFollowedSC(CONSTRUCTION) && spentStrategyToken ? FOLLOW : OTHER;
    }

    private static boolean holds(Player seat) {
        return seat.getSecretsUnscored().containsKey(ESTABLISH_A_PERIMETER)
                || seat.getSecretsScored().containsKey(ESTABLISH_A_PERIMETER);
    }

    private static Counts counts(Game game, Player seat) {
        Tile home = seat.getHomeSystemTile();
        return new Counts(
                count(game, seat, "pds"),
                count(game, seat, "sd"),
                home == null ? 0 : BoardView.countInTile(home, seat, UnitType.Pds),
                home == null ? 0 : BoardView.countInTile(home, seat, UnitType.Spacedock),
                seat.getStrategicCC());
    }

    private static int count(Game game, Player seat, String unit) {
        return ButtonHelper.getNumberOfUnitsOnTheBoard(game, seat, unit);
    }
}
