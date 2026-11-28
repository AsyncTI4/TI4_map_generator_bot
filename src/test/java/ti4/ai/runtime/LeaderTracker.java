package ti4.ai.runtime;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import ti4.game.Game;
import ti4.game.Leader;
import ti4.game.Player;

// What every seat did with its agent, commander and hero in one self-play game: each change of a leader's state
// (unlocked, exhausted, readied, active, gone) read from the game state between ticks, and every leader-related button
// the AI pressed with the reason it gave.
final class LeaderTracker {

    private static final Pattern LEADER_BUTTON = Pattern.compile(
            "(?i)agent|commander|hero|exhaustForVotes|purge|leader|envoy|carth|gila|harrugh|quieron|unlenn|treilla|farran|pick (Imperial|Warfare)|play (Imperial|Warfare)");

    private final Map<String, String> states = new HashMap<>();
    private final Map<String, String> breakthroughs = new HashMap<>();
    private final List<String> lines = new ArrayList<>();
    private int round;

    synchronized void observe(Game game) {
        if (game == null) return;
        round = game.getRound();
        for (Player seat : game.getRealPlayers()) {
            observeBreakthroughs(seat);
            observeMecatol(seat);
            Map<String, String> now = new HashMap<>();
            for (Leader leader : seat.getLeaders()) now.put(leader.getId(), stateOf(leader));
            for (Map.Entry<String, String> entry : now.entrySet()) {
                String key = seat.getFaction() + "|" + entry.getKey();
                String before = states.put(key, entry.getValue());
                if (!entry.getValue().equals(before)) {
                    lines.add(String.format(
                            "r%d %s %s: %s -> %s",
                            round, seat.getFaction(), entry.getKey(), before == null ? "-" : before, entry.getValue()));
                }
            }
            for (String key : new ArrayList<>(states.keySet())) {
                if (!key.startsWith(seat.getFaction() + "|")) continue;
                String id = key.substring(key.indexOf('|') + 1);
                if (now.containsKey(id) || "gone".equals(states.get(key))) continue;
                states.put(key, "gone");
                lines.add(String.format("r%d %s %s: -> gone (purged or removed)", round, seat.getFaction(), id));
            }
        }
    }

    private void observeMecatol(Player seat) {
        String state = seat.controlsMecatol(true) ? "held" : "lost";
        String before = breakthroughs.put(seat.getFaction() + "/mecatol", state);
        if (before == null && "lost".equals(state) || state.equals(before)) return;
        lines.add(String.format("r%d %s mecatol: %s", round, seat.getFaction(), state));
    }

    private void observeGravityDrive(Player seat) {
        String state = seat.hasTech("gd") ? "owned" : "missing";
        String before = breakthroughs.put(seat.getFaction() + "/gd", state);
        if (!state.equals(before) && "owned".equals(state)) {
            lines.add(String.format("r%d %s gravitydrive: owned", round, seat.getFaction()));
        }
    }

    private void observeBreakthroughs(Player seat) {
        observeGravityDrive(seat);
        for (String id : seat.getBreakthroughIDs()) {
            String state = seat.isBreakthroughUnlocked(id) ? "unlocked" : "locked";
            String before = breakthroughs.put(seat.getFaction() + "/" + id, state);
            if (!state.equals(before)) {
                lines.add(String.format(
                        "r%d %s %s: %s -> %s (fleet pool %d)",
                        round, seat.getFaction(), id, before == null ? "-" : before, state, seat.getFleetCC()));
            }
        }
    }

    synchronized void pressed(int round, String faction, String customId, String outcome) {
        if (!LEADER_BUTTON.matcher(customId).find()) return;
        lines.add(String.format("r%d %s pressed %s -> %s", round, faction, customId, outcome));
    }

    synchronized void note(int round, String faction, String text) {
        lines.add(String.format("r%d %s %s", round, faction, text));
    }

    synchronized void decided(int round, String faction, String reason) {
        if (!LEADER_BUTTON.matcher(reason).find()) return;
        lines.add(String.format("r%d %s decided: %s", round, faction, reason));
    }

    synchronized List<String> lines() {
        return List.copyOf(lines);
    }

    private static String stateOf(Leader leader) {
        if (leader.isLocked()) return "locked";
        if (leader.isActive()) return "active";
        return leader.isExhausted() ? "exhausted" : "ready";
    }
}
