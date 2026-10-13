package ti4.helpers.thirtyk;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.experimental.UtilityClass;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
public class ThirtyKTransactions {

    static final String GRANTS_KEY = "thirtyKTransactGrants";
    private static final String TURN = "turn";
    private static final String ACTION_PHASE = "phase";

    public static void allowForThisTurn(Game game, Player player, Player other) {
        addGrant(game, player, other, TURN);
    }

    public static void allowForThisActionPhase(Game game, Player player, Player other) {
        addGrant(game, player, other, ACTION_PHASE);
    }

    public static boolean mayTransact(Game game, Player player, Player other) {
        if (player == null || other == null) return false;
        return liveGrants(game).stream().anyMatch(grant -> grant.covers(player, other));
    }

    private static void addGrant(Game game, Player player, Player other, String scope) {
        Player active = game.getActivePlayer();
        Grant grant = new Grant(
                player.getFaction(),
                other.getFaction(),
                scope,
                game.getRound(),
                active == null ? "" : active.getFaction(),
                active == null ? 0 : active.getNumberOfTurns());
        List<Grant> grants = new ArrayList<>(liveGrants(game));
        grants.add(grant);
        game.setStoredValue(
                GRANTS_KEY,
                String.join(";", grants.stream().map(Grant::serialize).toList()));
    }

    private static List<Grant> liveGrants(Game game) {
        String stored = game.getStoredValue(GRANTS_KEY);
        if (stored.isBlank()) return List.of();
        return Arrays.stream(stored.split(";"))
                .map(Grant::parse)
                .filter(grant -> grant != null && grant.isLive(game))
                .toList();
    }

    private record Grant(
            String faction, String otherFaction, String scope, int round, String activeFaction, int turns) {

        String serialize() {
            return String.join(
                    "+", faction, otherFaction, scope, String.valueOf(round), activeFaction, String.valueOf(turns));
        }

        static Grant parse(String raw) {
            String[] parts = raw.split("\\+", -1);
            if (parts.length != 6) return null;
            try {
                return new Grant(
                        parts[0], parts[1], parts[2], Integer.parseInt(parts[3]), parts[4], Integer.parseInt(parts[5]));
            } catch (NumberFormatException e) {
                return null;
            }
        }

        boolean covers(Player player, Player other) {
            return (faction.equals(player.getFaction()) && otherFaction.equals(other.getFaction()))
                    || (faction.equals(other.getFaction()) && otherFaction.equals(player.getFaction()));
        }

        boolean isLive(Game game) {
            if (round != game.getRound()) return false;
            if (ACTION_PHASE.equals(scope)) return "action".equalsIgnoreCase(game.getPhaseOfGame());
            Player active = game.getActivePlayer();
            return active != null && active.getFaction().equals(activeFaction) && active.getNumberOfTurns() == turns;
        }
    }
}
