package ti4.service.draft;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import lombok.Data;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.service.map.AddTileListService;

/**
 * Manages the state of the draft, including:
 * - Players involved
 * - Orchestrator
 * - Draftables available
 * - Routing interactions
 * - Lifecycle management (starting draft, ending draft, setting up players, etc)
 *   - This includes checking that all components are ready to proceed at each stage.
 * - Validating state consistency
 */
@Data
public class DraftManager {
    public DraftManager(Game game) {
        if (game == null) {
            throw new IllegalArgumentException("Game cannot be null");
        }
        if (draftables == null) {
            throw new IllegalArgumentException("Draftables cannot be null");
        }
        this.game = game;
        // this.orchestrator = orchestrator;
        // this.draftables = new ArrayList<>(draftables);
        // this.playerStates =
        // players.stream().collect(HashMap::new, (m, p) -> m.put(p, new
        // PlayerDraftState()), Map::putAll);
    }

    private final Game game;
    private DraftOrchestrator orchestrator = null;
    // The order of draftables is the correct order for summarizing, applying, etc.
    private final List<Draftable> draftables = new ArrayList<>();
    // Key: Player's UserID
    private final Map<String, PlayerDraftState> playerStates = new HashMap<>();

    // Setup

    public void setPlayers(List<String> players) {
        if (players == null || players.isEmpty()) {
            throw new IllegalArgumentException("Players cannot be null or empty");
        }
        for (String player : players) {
            if (playerStates.containsKey(player)) {
                throw new IllegalArgumentException("Duplicate player: " + player);
            }
            playerStates.put(player, new PlayerDraftState());
        }
    }

    public void setOrchestrator(DraftOrchestrator orchestrator) {
        if (orchestrator == null) {
            throw new IllegalArgumentException("Orchestrator cannot be null");
        }
        if (this.orchestrator != null) {
            throw new IllegalStateException("Orchestrator is already set");
        }
        this.orchestrator = orchestrator;
    }

    public void addDraftable(Draftable draftable) {
        if (draftable == null) {
            throw new IllegalArgumentException("Draftable cannot be null");
        }
        for (Draftable d : draftables) {
            if (d.getType().equals(draftable.getType())) {
                throw new IllegalArgumentException("Draftable of type " + draftable.getType() + " is already added");
            }
        }
        draftables.add(draftable);
    }

    public void resetForNewDraft() {
        this.orchestrator = null;
        this.draftables.clear();
        this.playerStates.clear();
    }

    // Player management

    public void replacePlayer(String oldUserId, String newUserId) {
        if (!playerStates.containsKey(oldUserId)) {
            throw new IllegalArgumentException("Cannot replace player " + oldUserId + "; not in draft");
        }
        if (playerStates.containsKey(newUserId)) {
            throw new IllegalArgumentException("Cannot replace player with " + newUserId + "; already in draft");
        }

        PlayerDraftState state = playerStates.remove(oldUserId);
        playerStates.put(newUserId, state);
    }

    public void swapPlayers(String userId1, String userId2) {
        if (!playerStates.containsKey(userId1)) {
            throw new IllegalArgumentException("Cannot swap player " + userId1 + "; not in draft");
        }
        if (!playerStates.containsKey(userId2)) {
            throw new IllegalArgumentException("Cannot swap player " + userId2 + "; not in draft");
        }

        PlayerDraftState state1 = playerStates.get(userId1);
        PlayerDraftState state2 = playerStates.get(userId2);
        playerStates.put(userId1, state2);
        playerStates.put(userId2, state1);
    }

    // Information Access

    public List<DraftChoice> getPlayerChoices(String playerUserId, DraftableType type) {
        if (!playerStates.containsKey(playerUserId)) {
            throw new IllegalArgumentException("Player " + playerUserId + " is not in the draft");
        }
        PlayerDraftState pState = playerStates.get(playerUserId);
        if (!pState.getPicks().containsKey(type)) {
            return List.of();
        }
        List<DraftChoice> choices = pState.getPicks().get(type);
        return choices;
    }

    public List<String> getPlayersWithChoiceKey(DraftableType type, String choiceKey) {
        List<String> playersWithChoice = new ArrayList<>();
        for (String userId : playerStates.keySet()) {
            List<DraftChoice> choices = getPlayerChoices(userId, type);
            for (DraftChoice choice : choices) {
                if (choice.getChoiceKey().equals(choiceKey)) {
                    playersWithChoice.add(userId);
                    break;
                }
            }
        }
        return playersWithChoice;
    }

    public List<DraftChoice> getAllPicksOfType(DraftableType type) {
        List<DraftChoice> allChoices = new ArrayList<>();
        for (String userId : playerStates.keySet()) {
            PlayerDraftState pState = playerStates.get(userId);
            if (pState.getPicks().containsKey(type)) {
                allChoices.addAll(pState.getPicks().get(type));
            }
        }
        return allChoices;
    }

    public Draftable getDraftableByType(DraftableType type) {
        for (Draftable d : draftables) {
            if (d.getType().equals(type)) {
                return d;
            }
        }
        return null;
    }

    // Interaction handling

    public String routeCommand(GenericInteractionCreateEvent event, Player player, String command) {
        for (Draftable d : draftables) {
            String commandPrefix = d.getCommandKey() + "_";
            if (command.startsWith(commandPrefix)) {
                String innerCommand = command.substring(commandPrefix.length());
                for (DraftChoice choice : d.getAllDraftChoices()) {
                    if (innerCommand.equals(choice.getChoiceKey())) {
                        if (orchestrator == null) {
                            throw new IllegalStateException("Draft choice command issued, but no orchestrator is set");
                        }
                        String validationError = d.isValidDraftChoice(this, player.getUserID(), choice);
                        if (validationError != null) {
                            return validationError;
                        }
                        // More validation, and add the button to the player's state
                        String status = orchestrator.applyDraftChoice(event, this, player.getUserID(), choice);
                        if (DraftButtonService.isError(status)) {
                            return status;
                        }
                        // Side effects, if any
                        d.postApplyDraftChoice(event, this, player.getUserID(), choice);

                        // After this choice, check if the draft is over.
                        tryEndDraft(event);

                        return status;
                    }
                }

                String status = d.handleCustomCommand(event, this, player.getUserID(), innerCommand);
                return status;
            }
        }

        if (orchestrator != null && command.startsWith(orchestrator.getButtonPrefix())) {
            String innerButtonID =
                    command.substring(orchestrator.getButtonPrefix().length());
            String status = orchestrator.handleCustomButtonPress(event, this, player.getUserID(), innerButtonID);
            return status;
        }

        throw new IllegalArgumentException("Button ID " + command + " not recognized by draft manager");
    }

    // Lifecycle management

    /**
     * Whenever something blocking the draft from starting is resolved, this should
     * be called.
     */
    public void tryStartDraft() {
        if (!canStartDraft()) {
            return;
        }

        orchestrator.startDraft(this);
    }

    public boolean canStartDraft() {
        if (draftables.isEmpty() || orchestrator == null) {
            return false;
        }

        // Consider checking for minimal draftables here...something that provides a faction,
        // something that builds a map, etc.

        return true;
    }

    public void tryEndDraft(GenericInteractionCreateEvent event) {
        if (getBlockingDraftEndReason() != null) {
            return;
        }

        endDraft(event);
    }

    /**
     * End the selection of draft choices, and begin doing post-draft work.
     * If any component needs to get player interaction to complete its work, it
     * should send buttons now.
     * If all components are ready to setup players, do so here. Otherwise call
     * trySetupPlayers() whenever something blocking is resolved.
     *
     * @param event
     */
    public String endDraft(GenericInteractionCreateEvent event) {
        String blockingReason = getBlockingDraftEndReason();
        if (blockingReason != null) {
            // If you got this accidentally, it means you called endDraft() instead of tryEndDraft().
            // If there was an issue and someone used a slash command to force-end the draft, that's fine.
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), "WARNING: Forcing the draft to end despite: " + blockingReason);
        }

        for (Draftable draftable : draftables) {
            draftable.onDraftEnd(this);
        }

        trySetupPlayers(event);
        return null;
    }

    /**
     * Determine whether all lifecycle components are ready to end the picking of draft
     * choices.
     *
     * @return Null if ready to end the draft, or a SPECIFIC message describing what is being waited on.
     */
    public String getBlockingDraftEndReason() {
        for (Draftable d : draftables) {
            String reason = d.getBlockingDraftEndReason(this);
            if (reason != null) {
                return reason;
            }
        }
        return orchestrator.getBlockingDraftEndReason(this);
    }

    /**
     * Attempt to do player setup and start the game. If any component is not ready,
     * return without doing anything.
     *
     * @param event
     */
    public void trySetupPlayers(GenericInteractionCreateEvent event) {
        if (getBlockingSetupReason() != null) {
            return;
        }

        for (String userId : playerStates.keySet()) {
            PlayerSetupService.PlayerSetupState playerSetupState = new PlayerSetupService.PlayerSetupState();
            List<Consumer<Player>> postSetupActions = new ArrayList<>();
            for (Draftable draftable : draftables) {
                Consumer<Player> postSetupAction = draftable.setupPlayer(this, userId, playerSetupState);
                if (postSetupAction != null) {
                    postSetupActions.add(postSetupAction);
                }
            }
            Player player = game.getPlayer(userId);
            if (playerSetupState.getColor() == null) {
                String color = player.getNextAvailableColour();
                playerSetupState.setColor(color);
            }
            PlayerSetupService.setupPlayer(playerSetupState, player, game, event);

            for (Consumer<Player> action : postSetupActions) {
                action.accept(player);
            }
        }

        AddTileListService.finishSetup(game, event);
    }

    private String getBlockingSetupReason() {
        for (Draftable d : draftables) {
            String result = d.getBlockingSetupReason(this);
            if (result != null) {
                return result;
            }
        }
        return orchestrator.getBlockingSetupReason(this);
    }

    /**
     * Check that all required fields are set and that all shared state is
     * consistent.
     */
    public void validateState() {
        if (game == null) {
            throw new IllegalStateException("Game not set");
        }
        if (draftables.isEmpty()) {
            throw new IllegalStateException("Draftables not set");
        }
        if (orchestrator == null) {
            throw new IllegalStateException("Orchestrator not set");
        }
        if (playerStates == null || playerStates.isEmpty()) {
            throw new IllegalStateException("No players in draft");
        }
        // TODO: What could we do here instead, to confirm we have the correct number of players?
        // if (playerStates.size() != game.getRealPlayers().size()) {
        // throw new IllegalStateException("Number of players in draft does not match
        // number of players in game");
        // }
        if (draftables.isEmpty()) {
            throw new IllegalStateException("No draftables in draft");
        }
        orchestrator.validateState(this);
        for (Draftable d : draftables) {
            d.validateState(this);
        }

        // TODO:
        // All DraftChoices and Draftables conform to stated requirements for their given strings,
        // e.g. getChoiceKey() is non-null, non-empty, lowercase alpha-numeric only, etc.

        // TODO:
        // All DraftChoices across all Draftables have unique choice keys.
    }
}
