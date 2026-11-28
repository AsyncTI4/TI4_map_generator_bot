package ti4.ai.runtime;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import ti4.game.Game;
import ti4.game.Player;

/**
 * What one self-play game did with its relics, read from the game state between ticks: when a seat first held a relic,
 * when a relic left a seat (purged, or passed to another seat as the Shard of the Throne is), and each time an exhaust
 * relic went from ready to exhausted. Points are the holder's at the end of the game.
 */
final class RelicTracker {

    // A relic gained by a seat in a round; fromSeat is the previous holder when it changed hands.
    record Gain(int round, String relic, String faction, String fromSeat) {}

    // A relic leaving a seat for good (purged) or exhausted for its action; round is the game's round when seen.
    record Spend(int round, String relic, String faction, boolean purged) {}

    record Summary(List<Gain> gains, List<Spend> spends, Map<String, String> heldAtEnd, Map<String, Integer> points) {

        static final Summary NOTHING = new Summary(List.of(), List.of(), Map.of(), Map.of());
    }

    private final Map<String, String> holders = new HashMap<>();
    private final Set<String> exhausted = new HashSet<>();
    private final List<Gain> gains = new ArrayList<>();
    private final List<Spend> spends = new ArrayList<>();

    synchronized void observe(Game game) {
        if (game == null) return;
        Map<String, String> now = new HashMap<>();
        Set<String> nowExhausted = new HashSet<>();
        for (Player seat : game.getRealPlayers()) {
            for (String relic : seat.getRelics()) {
                now.put(relic, seat.getFaction());
                if (seat.getExhaustedRelics().contains(relic)) nowExhausted.add(relic + "|" + seat.getFaction());
            }
        }
        for (Map.Entry<String, String> entry : now.entrySet()) {
            String before = holders.get(entry.getKey());
            if (!entry.getValue().equals(before)) {
                gains.add(new Gain(game.getRound(), entry.getKey(), entry.getValue(), before));
            }
        }
        for (Map.Entry<String, String> entry : holders.entrySet()) {
            if (!now.containsKey(entry.getKey())) {
                spends.add(new Spend(game.getRound(), entry.getKey(), entry.getValue(), true));
            }
        }
        for (String key : nowExhausted) {
            if (!exhausted.contains(key)) {
                String[] parts = key.split("\\|");
                spends.add(new Spend(game.getRound(), parts[0], parts[1], false));
            }
        }
        holders.clear();
        holders.putAll(now);
        exhausted.clear();
        exhausted.addAll(nowExhausted);
    }

    synchronized Summary summary(Game game) {
        if (game == null) return new Summary(List.copyOf(gains), List.copyOf(spends), Map.of(), Map.of());
        observe(game);
        Map<String, Integer> points = new HashMap<>();
        for (Player seat : game.getRealPlayers()) points.put(seat.getFaction(), seat.getTotalVictoryPoints());
        return new Summary(List.copyOf(gains), List.copyOf(spends), Map.copyOf(holders), Map.copyOf(points));
    }
}
