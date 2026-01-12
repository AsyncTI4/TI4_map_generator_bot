package ti4.ai;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ti4.map.Game;
import ti4.map.Player;

import java.util.List;
import java.util.Map;

/**
 * Interface for AI player decision-making strategies.
 * Implementations should make legal, deterministic decisions for various game phases.
 * All methods should return null to indicate "pass" or "no action" when no beneficial move is found.
 */
public interface PlayerStrategy {

    /**
     * Choose an action to take during the action phase.
     * 
     * @param game The current game state
     * @param player The AI player making the decision
     * @return Action command to execute (e.g., "tactical", "strategic"), or null to pass
     */
    @Nullable
    String chooseAction(@NotNull Game game, @NotNull Player player);

    /**
     * Choose a strategy card during the strategy phase.
     * 
     * @param game The current game state
     * @param player The AI player making the decision
     * @param availableCards List of available strategy card IDs
     * @return Strategy card ID to pick, or null to pass/delay
     */
    @Nullable
    Integer chooseStrategyCard(@NotNull Game game, @NotNull Player player, @NotNull List<Integer> availableCards);

    /**
     * Choose a tactical action target (system to activate).
     * 
     * @param game The current game state
     * @param player The AI player making the decision
     * @return Tile position to activate, or null to pass
     */
    @Nullable
    String chooseTacticalTarget(@NotNull Game game, @NotNull Player player);

    /**
     * Choose which units to move during a tactical action.
     * 
     * @param game The current game state
     * @param player The AI player making the decision
     * @param fromPosition The source tile position
     * @param toPosition The destination tile position
     * @return Map of unit type to count to move, or empty map for no movement
     */
    @NotNull
    Map<String, Integer> chooseUnitsToMove(@NotNull Game game, @NotNull Player player, 
                                            @NotNull String fromPosition, @NotNull String toPosition);

    /**
     * Choose a technology to research.
     * 
     * @param game The current game state
     * @param player The AI player making the decision
     * @param availableTechs List of available tech IDs
     * @return Technology ID to research, or null to skip
     */
    @Nullable
    String chooseTechnology(@NotNull Game game, @NotNull Player player, @NotNull List<String> availableTechs);

    /**
     * Choose whether to explore and which explore type to use.
     * 
     * @param game The current game state
     * @param player The AI player making the decision
     * @param position Tile position where exploration is available
     * @return Explore type (e.g., "cultural", "industrial", "hazardous"), or null to skip
     */
    @Nullable
    String chooseExploration(@NotNull Game game, @NotNull Player player, @NotNull String position);

    /**
     * Choose how to vote on an agenda.
     * 
     * @param game The current game state
     * @param player The AI player making the decision
     * @param agendaId The agenda being voted on
     * @param availableOutcomes List of possible vote outcomes
     * @return Vote outcome and optional number of votes, or null to abstain
     */
    @Nullable
    AgendaVote chooseAgendaVote(@NotNull Game game, @NotNull Player player, 
                                 @NotNull String agendaId, @NotNull List<String> availableOutcomes);

    /**
     * Choose which secret objective to score (if any).
     * 
     * @param game The current game state
     * @param player The AI player making the decision
     * @return Secret objective ID to score, or null if none can be scored
     */
    @Nullable
    String chooseSecretObjectiveToScore(@NotNull Game game, @NotNull Player player);

    /**
     * Choose whether to use an action card.
     * 
     * @param game The current game state
     * @param player The AI player making the decision
     * @param actionCardId The action card being considered
     * @return true to play the card, false to keep it
     */
    boolean shouldPlayActionCard(@NotNull Game game, @NotNull Player player, @NotNull String actionCardId);

    /**
     * Get the difficulty level of this strategy.
     * 
     * @return Difficulty level (e.g., "simple", "medium", "hard")
     */
    @NotNull
    String getDifficulty();

    /**
     * Container for agenda vote decision including outcome and vote count.
     */
    class AgendaVote {
        private final String outcome;
        private final Integer votes;

        public AgendaVote(String outcome, Integer votes) {
            this.outcome = outcome;
            this.votes = votes;
        }

        public String getOutcome() {
            return outcome;
        }

        public Integer getVotes() {
            return votes;
        }
    }
}
