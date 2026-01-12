package ti4.ai;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.logging.BotLogger;

import java.util.*;

/**
 * Simple heuristic strategy for AI players.
 * Prioritizes safe, legal actions with basic value heuristics.
 * Defaults to passing when no beneficial action is found.
 */
public class SimpleHeuristicStrategy implements PlayerStrategy {

    @Override
    @Nullable
    public String chooseAction(@NotNull Game game, @NotNull Player player) {
        try {
            // Basic action priority: research tech > build > explore > pass
            // This is a simplified decision tree - real implementation would check legality

            // Check if player can research tech
            if (canResearchTech(player)) {
                return "research";
            }

            // Check if player can build
            if (canBuild(player)) {
                return "build";
            }

            // Check if tactical action is beneficial
            if (shouldTakeTacticalAction(game, player)) {
                return "tactical";
            }

            // Default to pass
            return null;
        } catch (Exception e) {
            BotLogger.error("SimpleHeuristicStrategy.chooseAction error", e);
            return null;
        }
    }

    @Override
    @Nullable
    public Integer chooseStrategyCard(@NotNull Game game, @NotNull Player player, @NotNull List<Integer> availableCards) {
        if (availableCards.isEmpty()) {
            return null;
        }

        // Simple heuristic: prefer Technology (7) > Warfare (6) > Trade (5) > others
        List<Integer> priorityOrder = Arrays.asList(7, 6, 5, 4, 3, 2, 1, 8);
        
        for (Integer preferred : priorityOrder) {
            if (availableCards.contains(preferred)) {
                return preferred;
            }
        }

        // Fall back to first available
        return availableCards.get(0);
    }

    @Override
    @Nullable
    public String chooseTacticalTarget(@NotNull Game game, @NotNull Player player) {
        // Simple heuristic: choose unoccupied adjacent system or pass
        // Real implementation would analyze board state
        return null; // Default to pass for safety
    }

    @Override
    @NotNull
    public Map<String, Integer> chooseUnitsToMove(@NotNull Game game, @NotNull Player player,
                                                    @NotNull String fromPosition, @NotNull String toPosition) {
        // Simple heuristic: move minimal force
        // Real implementation would calculate needed strength
        return Collections.emptyMap();
    }

    @Override
    @Nullable
    public String chooseTechnology(@NotNull Game game, @NotNull Player player, @NotNull List<String> availableTechs) {
        if (availableTechs.isEmpty()) {
            return null;
        }

        // Simple heuristic: prefer unit upgrade techs, then economic techs
        List<String> preferredTechs = Arrays.asList(
            "daxcive", // Hyper Metabolism (unit upgrade)
            "sarween", // Sarween Tools (production)
            "nn",      // Neural Motivator (command tokens)
            "iihq"     // Integrated Economy (resources)
        );

        for (String preferred : preferredTechs) {
            if (availableTechs.contains(preferred)) {
                return preferred;
            }
        }

        // Pick first available if no preference matches
        return availableTechs.get(0);
    }

    @Override
    @Nullable
    public String chooseExploration(@NotNull Game game, @NotNull Player player, @NotNull String position) {
        // Simple heuristic: always explore cultural first (safest), then industrial, then hazardous
        // Real implementation would check what's available
        return "cultural";
    }

    @Override
    @Nullable
    public AgendaVote chooseAgendaVote(@NotNull Game game, @NotNull Player player,
                                        @NotNull String agendaId, @NotNull List<String> availableOutcomes) {
        if (availableOutcomes.isEmpty()) {
            return null;
        }

        // Simple heuristic: vote for first option with minimal votes
        // Real implementation would analyze agenda impact
        String outcome = availableOutcomes.get(0);
        int votes = Math.min(1, player.getTg()); // Vote with 1 influence if available
        
        return new AgendaVote(outcome, votes);
    }

    @Override
    @Nullable
    public String chooseSecretObjectiveToScore(@NotNull Game game, @NotNull Player player) {
        // Simple heuristic: check each secret objective for completion
        // Real implementation would validate scoring conditions
        return null; // Default to not score until validation logic is added
    }

    @Override
    public boolean shouldPlayActionCard(@NotNull Game game, @NotNull Player player, @NotNull String actionCardId) {
        // Simple heuristic: don't play action cards unless critical
        // Real implementation would evaluate card value and timing
        return false;
    }

    @Override
    @NotNull
    public String getDifficulty() {
        return "simple";
    }

    // Helper methods for checking preconditions

    private boolean canResearchTech(@NotNull Player player) {
        // Simple check: has at least 4 resources (using trade goods as proxy)
        return player.getTg() >= 4;
    }

    private boolean canBuild(@NotNull Player player) {
        // Simple check: has at least 2 resources (using trade goods as proxy)
        return player.getTg() >= 2;
    }

    private boolean shouldTakeTacticalAction(@NotNull Game game, @NotNull Player player) {
        // Simple check: has command tokens and hasn't passed
        return player.getTacticalCC() > 0 && !player.isPassed();
    }
}
