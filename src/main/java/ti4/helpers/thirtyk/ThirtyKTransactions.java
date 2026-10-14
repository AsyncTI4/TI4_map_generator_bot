package ti4.helpers.thirtyk;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import lombok.experimental.UtilityClass;
import ti4.game.Game;
import ti4.game.Player;
import ti4.service.fow.FowCommunicationThreadService;

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
        if (!ThirtyKCards.inUse(game)) return false;
        if (player == null || other == null) return false;
        return grants(game).stream().anyMatch(grant -> grant.isLive(game) && grant.covers(player, other))
                || ThirtyKRelicHooks.deepNetworksAllows(game, player, other);
    }

    public static Set<Player> partnersOf(Game game, Player player) {
        if (!ThirtyKCards.inUse(game)) return new HashSet<>();
        Set<Player> partners = new HashSet<>();
        for (Player other : game.getRealPlayers()) {
            if (other != player && mayTransact(game, player, other)) partners.add(other);
        }
        return partners;
    }

    static void clearTurnGrants(Game game) {
        keepGrants(game, grant -> !TURN.equals(grant.scope()));
    }

    static void clearAllGrants(Game game) {
        game.removeStoredValue(GRANTS_KEY);
    }

    private static void keepGrants(Game game, Predicate<Grant> keep) {
        if (game.getStoredValue(GRANTS_KEY).isBlank()) return;
        storeGrants(game, grants(game).stream().filter(keep).toList());
    }

    private static void addGrant(Game game, Player player, Player other, String scope) {
        List<Grant> grants = new ArrayList<>(grants(game));
        grants.add(new Grant(player.getFaction(), other.getFaction(), scope));
        storeGrants(game, grants);
        FowCommunicationThreadService.checkNewCommPartners(game, player);
        FowCommunicationThreadService.checkNewCommPartners(game, other);
    }

    private static void storeGrants(Game game, List<Grant> grants) {
        if (grants.isEmpty()) {
            game.removeStoredValue(GRANTS_KEY);
            return;
        }
        game.setStoredValue(
                GRANTS_KEY,
                String.join(";", grants.stream().map(Grant::serialize).toList()));
    }

    private static List<Grant> grants(Game game) {
        String stored = game.getStoredValue(GRANTS_KEY);
        if (stored.isBlank()) return List.of();
        return Arrays.stream(stored.split(";"))
                .map(Grant::parse)
                .filter(grant -> grant != null)
                .toList();
    }

    private record Grant(String faction, String otherFaction, String scope) {

        String serialize() {
            return String.join("+", faction, otherFaction, scope);
        }

        static Grant parse(String raw) {
            String[] parts = raw.split("\\+", -1);
            if (parts.length < 3) return null;
            return new Grant(parts[0], parts[1], parts[2]);
        }

        boolean covers(Player player, Player other) {
            return (faction.equals(player.getFaction()) && otherFaction.equals(other.getFaction()))
                    || (faction.equals(other.getFaction()) && otherFaction.equals(player.getFaction()));
        }

        boolean isLive(Game game) {
            return !ACTION_PHASE.equals(scope) || "action".equalsIgnoreCase(game.getPhaseOfGame());
        }
    }
}
