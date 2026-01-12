package ti4.ai;

import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;
import ti4.message.logging.BotLogger;

/**
 * Wrapper for an AI-controlled player.
 * Holds configuration and delegates decisions to a PlayerStrategy.
 */
@Getter
public class AiPlayer {

    private final String gameId;
    private final String playerId;
    private final PlayerStrategy strategy;
    
    @Setter
    private boolean paused = false;
    
    @Setter
    private String difficulty;
    
    @Getter
    @Setter
    private int actionsTakenThisPhase = 0;

    /**
     * Create a new AI player.
     * 
     * @param gameId The game ID this AI is playing in
     * @param playerId The player ID (color) this AI controls
     * @param strategy The decision-making strategy to use
     * @param difficulty The difficulty level name
     */
    public AiPlayer(@NotNull String gameId, @NotNull String playerId, 
                    @NotNull PlayerStrategy strategy, @NotNull String difficulty) {
        this.gameId = gameId;
        this.playerId = playerId;
        this.strategy = strategy;
        this.difficulty = difficulty;
    }

    /**
     * Check if this AI player is currently active and can take actions.
     */
    public boolean isActive() {
        return !paused;
    }

    /**
     * Reset action counter for a new phase.
     */
    public void resetPhaseActions() {
        this.actionsTakenThisPhase = 0;
    }

    /**
     * Increment the action counter.
     */
    public void incrementActions() {
        this.actionsTakenThisPhase++;
    }

    /**
     * Log a decision made by this AI.
     */
    public void logDecision(String phase, String action, String details) {
        if (details == null) {
            details = "no details";
        }
        BotLogger.info(String.format(
            "[AI] Game=%s Player=%s Phase=%s Action=%s Details=%s",
            gameId, playerId, phase, action, details
        ));
    }

    /**
     * Log an error encountered by this AI.
     */
    public void logError(String context, Exception e) {
        BotLogger.error(String.format(
            "[AI ERROR] Game=%s Player=%s Context=%s",
            gameId, playerId, context
        ), e);
    }

    @Override
    public String toString() {
        return String.format("AiPlayer[game=%s, player=%s, difficulty=%s, paused=%s]",
            gameId, playerId, difficulty, paused);
    }
}
