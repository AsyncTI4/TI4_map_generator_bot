package ti4.service.statistics.round;

import java.util.function.Consumer;
import lombok.experimental.UtilityClass;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.logging.BotLogger;
import ti4.spring.context.SpringContext;
import ti4.spring.service.roundstats.GameRoundStatsService;

@UtilityClass
public class RoundStatsTracker {

    public static void refreshOnSave(Game game, int createdUndoIndex) {
        withService(service -> service.refreshOnSave(game, createdUndoIndex));
    }

    public static void restoreAfterUndo(Game game, int targetUndoIndex) {
        withService(service -> service.restoreAfterUndo(game, targetUndoIndex));
    }

    public static void incrementCombatsInitiated(Game game, Player player) {
        withService(service -> service.incrementCombatsInitiated(game, player));
    }

    public static void markTacticalStart(Game game, Player player) {
        withService(service -> service.markTacticalStart(game, player));
    }

    public static void finalizeTactical(Game game, Player player) {
        withService(service -> service.finalizeTactical(game, player));
    }

    public static void clearTacticalMarkers(Game game, Player player) {
        withService(service -> service.clearTacticalMarkers(game, player));
    }

    public static void recordPlanetTaken(Game game, Player player, boolean stolen) {
        withService(service -> service.recordPlanetTaken(game, player, stolen));
    }

    public static void recordTechGained(Game game, Player player, String techId) {
        withService(service -> service.recordTechGained(game, player, techId));
    }

    public static void recordDiceRolled(Game game, Player player, int diceCount) {
        withService(service -> service.recordDiceRolled(game, player, diceCount));
    }

    public static void recordTurnTime(Game game, Player player, long effectiveMs) {
        withService(service -> service.recordTurnTime(game, player, effectiveMs));
    }

    private static void withService(Consumer<GameRoundStatsService> operation) {
        try {
            operation.accept(SpringContext.getBean(GameRoundStatsService.class));
        } catch (IllegalStateException ignored) {
            // Spring not initialized (e.g. some tests/startup paths)
        } catch (Exception e) {
            BotLogger.error("Round stats tracking failed.", e);
        }
    }
}
