package ti4.ai.agenda;

import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.math.NumberUtils;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
public class AgendaSwing {

    public static final String CONFOUNDING_LEGAL_TEXT = "Confounding Legal Text";
    public static final String DEADLY_PLOT = "Deadly Plot";
    public static final String BRIBERY = "Bribery";
    public static final String VETO = "Veto";
    private static final Set<String> TITLES = Set.of(CONFOUNDING_LEGAL_TEXT, DEADLY_PLOT, BRIBERY, VETO);
    private static final double NO_EFFECT = 0;
    private static final String PIECES = ";";
    private static final char OWNER = '_';

    public static boolean changesTheAgenda(String title) {
        return TITLES.contains(title);
    }

    public static double forViewer(Game game, Player viewer, String title, Player cardPlayer) {
        AgendaForecast forecast = AgendaForecast.of(game, viewer);
        if (forecast.isEmpty() || cardPlayer == null) return 0;
        OptionalDouble after = utilityAfter(game, forecast, viewer, title, cardPlayer);
        if (after.isEmpty()) return 0;
        return after.getAsDouble() - forecast.expectedUtility(viewer);
    }

    public static Optional<String> currentWinner(Game game, Player viewer) {
        return AgendaForecast.of(game, viewer).mostLikely();
    }

    public static boolean isElectable(Game game, Player player) {
        return AgendaOutcomes.of(game).contains(player.getFaction());
    }

    public static boolean backedAnotherOutcome(Game game, Player player, String winner) {
        for (Map.Entry<String, String> entry : game.getCurrentAgendaVotes().entrySet()) {
            if (entry.getKey().equalsIgnoreCase(winner)) continue;
            for (String piece : entry.getValue().split(PIECES)) {
                if (ownedBy(game, piece, player)) return true;
            }
        }
        return false;
    }

    private static OptionalDouble utilityAfter(
            Game game, AgendaForecast forecast, Player viewer, String title, Player cardPlayer) {
        return switch (title) {
            case CONFOUNDING_LEGAL_TEXT -> utilityOf(forecast, viewer, Optional.of(cardPlayer.getFaction()));
            case DEADLY_PLOT, VETO -> OptionalDouble.of(NO_EFFECT);
            case BRIBERY -> utilityOf(forecast, viewer, votedFor(game, cardPlayer));
            default -> OptionalDouble.empty();
        };
    }

    private static OptionalDouble utilityOf(AgendaForecast forecast, Player viewer, Optional<String> outcome) {
        return outcome.filter(forecast.outcomes()::contains)
                .map(chosen -> OptionalDouble.of(forecast.utility(viewer, chosen)))
                .orElse(OptionalDouble.empty());
    }

    public static Optional<String> votedFor(Game game, Player player) {
        for (Map.Entry<String, String> entry : game.getCurrentAgendaVotes().entrySet()) {
            for (String piece : entry.getValue().split(PIECES)) {
                if (ownedBy(game, piece, player) && NumberUtils.isDigits(afterOwner(piece))) {
                    return Optional.of(entry.getKey());
                }
            }
        }
        return Optional.empty();
    }

    private static boolean ownedBy(Game game, String piece, Player player) {
        int split = piece.indexOf(OWNER);
        if (split < 0) return false;
        return game.getPlayerFromColorOrFaction(piece.substring(0, split).toLowerCase()) == player;
    }

    private static String afterOwner(String piece) {
        return piece.substring(piece.indexOf(OWNER) + 1);
    }
}
