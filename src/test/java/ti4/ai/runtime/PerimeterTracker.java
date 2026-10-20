package ti4.ai.runtime;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;

/**
 * Per Establish a Perimeter holder at the start of each status phase: the PDS on the board, whether it held Construction
 * or followed it, the PDS and space docks that appeared on the board in the round (a dock placed by a follow while the
 * secret is held should be 0), and whether it scored.
 */
final class PerimeterTracker {

    private static final String ESTABLISH_A_PERIMETER = "eap";
    private static final int CONSTRUCTION = 4;
    private static final String PRIMARY = "primary";
    private static final String FOLLOW = "follow";
    private static final String OTHER = "other";

    private record Holding(
            String faction,
            int round,
            int pds,
            boolean primary,
            boolean followed,
            Map<String, Integer> placed,
            boolean scored) {}

    private final Map<String, Integer> pdsBefore = new HashMap<>();
    private final Map<String, Integer> docksBefore = new HashMap<>();
    private final Map<String, Map<String, Integer>> placedThisRound = new HashMap<>();
    private final List<Holding> holdings = new ArrayList<>();
    private int dockFollows;

    synchronized void observe(Game game) {
        for (Player seat : game.getRealPlayers()) {
            if (!holds(seat)) continue;
            String faction = seat.getFaction();
            int pds = count(game, seat, "pds");
            int docks = count(game, seat, "sd");
            Integer pdsThen = pdsBefore.put(faction, pds);
            Integer docksThen = docksBefore.put(faction, docks);
            if (pdsThen != null && pds > pdsThen) place(faction, "pds", role(game, seat), pds - pdsThen);
            if (docksThen != null && docks > docksThen) {
                String role = role(game, seat);
                place(faction, "dock", role, docks - docksThen);
                if (FOLLOW.equals(role) && seat.getSecretsUnscored().containsKey(ESTABLISH_A_PERIMETER)) {
                    dockFollows += docks - docksThen;
                }
            }
        }
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
                    played && seat.hasFollowedSC(CONSTRUCTION),
                    Map.copyOf(placedThisRound.getOrDefault(faction, Map.of())),
                    seat.getSecretsScored().containsKey(ESTABLISH_A_PERIMETER)));
            placedThisRound.remove(faction);
        }
    }

    synchronized List<String> lines(Game game) {
        if (holdings.isEmpty()) return List.of();
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
        lines.add(String.format(
                "Perimeter %s: docks placed by follows while held %d, holder-phases %d",
                game.getName(), dockFollows, holdings.size()));
        return lines;
    }

    private void place(String faction, String type, String role, int count) {
        placedThisRound
                .computeIfAbsent(faction, ignored -> new HashMap<>())
                .merge(type + "/" + role, count, Integer::sum);
    }

    private static String role(Game game, Player seat) {
        if (!game.getPlayedSCs().contains(CONSTRUCTION)) return OTHER;
        if (seat.getSCs().contains(CONSTRUCTION)) return PRIMARY;
        return seat.hasFollowedSC(CONSTRUCTION) ? FOLLOW : OTHER;
    }

    private static boolean holds(Player seat) {
        return seat.getSecretsUnscored().containsKey(ESTABLISH_A_PERIMETER)
                || seat.getSecretsScored().containsKey(ESTABLISH_A_PERIMETER);
    }

    private static int count(Game game, Player seat, String unit) {
        return ButtonHelper.getNumberOfUnitsOnTheBoard(game, seat, unit);
    }
}
