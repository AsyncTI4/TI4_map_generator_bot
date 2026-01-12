package ti4.ai;

import org.jetbrains.annotations.NotNull;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.logging.BotLogger;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Orchestrates an AI player's turn across different game phases.
 * Handles action execution, retries, and fallback to passing.
 */
public class AiTurnRunner {

    private final AiConfig config;
    private final AiPlayer aiPlayer;

    public AiTurnRunner(@NotNull AiConfig config, @NotNull AiPlayer aiPlayer) {
        this.config = config;
        this.aiPlayer = aiPlayer;
    }

    /**
     * Execute AI turn for the current phase.
     * 
     * @param game The current game state
     * @param player The player object for this AI
     * @return true if action was taken, false if passed or errored
     */
    public boolean executeTurn(@NotNull Game game, @NotNull Player player) {
        if (!aiPlayer.isActive()) {
            aiPlayer.logDecision(game.getPhaseOfGame(), "SKIPPED", "AI is paused");
            return false;
        }

        if (aiPlayer.getActionsTakenThisPhase() >= config.getMaxActionsPerPhase()) {
            aiPlayer.logDecision(game.getPhaseOfGame(), "PASS", "Max actions per phase reached");
            return false;
        }

        String phase = game.getPhaseOfGame();
        
        try {
            boolean actionTaken = switch (phase.toLowerCase()) {
                case "strategy" -> executeStrategyPhase(game, player);
                case "action" -> executeActionPhase(game, player);
                case "agenda" -> executeAgendaPhase(game, player);
                case "status" -> executeStatusPhase(game, player);
                default -> {
                    aiPlayer.logDecision(phase, "PASS", "Unknown phase");
                    yield false;
                }
            };

            if (actionTaken) {
                aiPlayer.incrementActions();
                addActionDelay();
            }

            return actionTaken;
            
        } catch (Exception e) {
            aiPlayer.logError("executeTurn[phase=" + phase + "]", e);
            return false;
        }
    }

    /**
     * Handle strategy card selection phase.
     */
    private boolean executeStrategyPhase(@NotNull Game game, @NotNull Player player) {
        // Check if player has already picked a strategy card
        if (player.getSCs() != null && !player.getSCs().isEmpty()) {
            return false;
        }

        // Get available strategy cards
        var availableCards = game.getScPlayed().entrySet().stream()
            .filter(entry -> !entry.getValue())
            .map(entry -> entry.getKey())
            .toList();

        if (availableCards.isEmpty()) {
            return false;
        }

        Integer chosenCard = aiPlayer.getStrategy().chooseStrategyCard(game, player, availableCards);
        
        if (chosenCard == null) {
            aiPlayer.logDecision("strategy", "PASS", "No strategy card chosen");
            return false;
        }

        // Log the intended action
        aiPlayer.logDecision("strategy", "PICK_SC", "Card=" + chosenCard);

        // In dry-run mode, don't execute
        if (config.isDryRun()) {
            BotLogger.info("[AI DRY-RUN] Would pick strategy card: " + chosenCard);
            return true;
        }

        // TODO: Execute actual strategy card picking command
        // This would require integrating with existing command services
        // For now, just log the decision
        BotLogger.info("[AI TODO] Execute strategy card pick: " + chosenCard);
        
        return true;
    }

    /**
     * Handle action phase.
     */
    private boolean executeActionPhase(@NotNull Game game, @NotNull Player player) {
        if (player.isPassed()) {
            return false;
        }

        String action = aiPlayer.getStrategy().chooseAction(game, player);
        
        if (action == null) {
            aiPlayer.logDecision("action", "PASS", "No beneficial action");
            // TODO: Execute pass command
            return false;
        }

        aiPlayer.logDecision("action", action.toUpperCase(), "");

        if (config.isDryRun()) {
            BotLogger.info("[AI DRY-RUN] Would take action: " + action);
            return true;
        }

        // TODO: Execute actual action command
        BotLogger.info("[AI TODO] Execute action: " + action);
        
        return true;
    }

    /**
     * Handle agenda phase.
     */
    private boolean executeAgendaPhase(@NotNull Game game, @NotNull Player player) {
        // Simple check: has player voted?
        // Real implementation would check actual voting state
        
        aiPlayer.logDecision("agenda", "VOTE", "Placeholder for voting logic");
        
        if (config.isDryRun()) {
            BotLogger.info("[AI DRY-RUN] Would vote on agenda");
            return true;
        }

        // TODO: Execute actual voting command
        BotLogger.info("[AI TODO] Execute agenda vote");
        
        return false;
    }

    /**
     * Handle status phase.
     */
    private boolean executeStatusPhase(@NotNull Game game, @NotNull Player player) {
        // Check for scorable secret objectives
        String secretToScore = aiPlayer.getStrategy().chooseSecretObjectiveToScore(game, player);
        
        if (secretToScore != null) {
            aiPlayer.logDecision("status", "SCORE_SECRET", "SO=" + secretToScore);
            
            if (config.isDryRun()) {
                BotLogger.info("[AI DRY-RUN] Would score secret objective: " + secretToScore);
                return true;
            }

            // TODO: Execute actual scoring command
            BotLogger.info("[AI TODO] Execute score secret: " + secretToScore);
            return true;
        }

        return false;
    }

    /**
     * Add a small delay with jitter between actions to prevent bursts.
     */
    private void addActionDelay() {
        try {
            long baseDelay = config.getActionDelay();
            long jitter = ThreadLocalRandom.current().nextLong(config.getActionJitter());
            Thread.sleep(baseDelay + jitter);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            aiPlayer.logError("addActionDelay", e);
        }
    }

    /**
     * Reset the AI's phase state when a new phase begins.
     */
    public void onPhaseChange(@NotNull String newPhase) {
        aiPlayer.resetPhaseActions();
        aiPlayer.logDecision(newPhase, "PHASE_START", "Actions reset");
    }
}
