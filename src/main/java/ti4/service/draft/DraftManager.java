package ti4.service.draft;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import lombok.Getter;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.helpers.ButtonHelper;
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
 * - Lifecycle management (starting draft, ending draft, setting up players,
 * etc)
 * - This includes checking that all components are ready to proceed at each
 * stage.
 * - Validating state consistency
 */
public class DraftManager extends DraftPlayerManager {
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

    @Getter
    private final Game game;

    @Getter
    private DraftOrchestrator orchestrator = null;
    // The order of draftables is the correct order for summarizing, applying, etc.
    @Getter
    private final List<Draftable> draftables = new ArrayList<>();

    // Setup

    @Override
    public void addPlayer(String playerUserId) {
        if (game.getPlayer(playerUserId) == null) {
            throw new IllegalArgumentException("Player " + playerUserId + " is not in the game");
        }
        super.addPlayer(playerUserId);
        if (this.orchestrator != null) {
            this.orchestrator.initializePlayerStates(this);
        }
    }

    public void setOrchestrator(DraftOrchestrator orchestrator) {
        if (orchestrator == null) {
            throw new IllegalArgumentException("Orchestrator cannot be null");
        }
        this.orchestrator = orchestrator;
        this.orchestrator.initializePlayerStates(this);
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
        super.resetForNewDraft();
    }

    // Information Access

    public Draftable getDraftableByType(DraftableType type) {
        for (Draftable d : draftables) {
            if (d.getType().equals(type)) {
                return d;
            }
        }
        return null;
    }

    public boolean hasBeenPicked(DraftableType type, String choiceKey) {
        return getAllPicksOfType(type).stream().anyMatch(c -> c.getChoiceKey().equals(choiceKey));
    }

    // Interaction handling

    public enum CommandSource {
        BUTTON,
        SLASH_COMMAND,
        AUTO_PICK
    }

    public String routeCommand(
            GenericInteractionCreateEvent event, Player player, String command, CommandSource commandSource) {
        for (Draftable d : draftables) {
            String commandPrefix = d.getDraftableCommandKey() + "_";
            if (command.startsWith(commandPrefix)) {
                String innerCommand = command.substring(commandPrefix.length());
                for (DraftChoice choice : d.getAllDraftChoices()) {
                    if (innerCommand.equals(choice.getChoiceKey())) {
                        if (whatsStoppingDraftEnd() == null) {
                            return "Cannot make draft picks after the draft has ended.";
                        }

                        if (orchestrator == null) {
                            throw new IllegalStateException("Draft choice command issued, but no orchestrator is set");
                        }
                        String validationError = d.isValidDraftChoice(this, player.getUserID(), choice);
                        if (validationError != null) {
                            return validationError;
                        }
                        // More validation, and add the button to the player's state
                        String status =
                                orchestrator.applyDraftChoice(event, this, player.getUserID(), choice, commandSource);
                        if (DraftButtonService.isError(status)) {
                            return status;
                        }
                        // Side effects, if any
                        d.postApplyDraftPick(event, this, player.getUserID(), choice);

                        // After this choice, check if the draft is over.
                        // (Auto picks will trigger this method in their own call stack)
                        if (commandSource != CommandSource.AUTO_PICK) {
                            tryEndDraft(event);
                        }

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

        orchestrator.sendDraftButtons(this);
    }

    public boolean canStartDraft() {
        if (draftables.isEmpty() || orchestrator == null) {
            return false;
        }

        

        // Consider checking for minimal draftables here...something that provides a
        // faction,
        // something that builds a map, etc.

        return whatsStoppingDraftStart() == null;
    }

    public String whatsStoppingDraftStart() {
        if(draftables.isEmpty()) {
            return "No draftables have been added to the draft. Try `/draft manage add_draftable`.";
        }
        for (Draftable d : draftables) {
            String reason = d.whatsStoppingDraftStart(this);
            if (reason != null) {
                return reason;
            }
        }
        if (orchestrator == null) {
            return "No orchestrator has been set for the draft. Try `/draft manage set_orchestrator`.";
        }
        String reason = orchestrator.whatsStoppingDraftStart(this);
        if (reason != null) {
            return reason;
        }
        return null;
    }

    public void tryEndDraft(GenericInteractionCreateEvent event) {
        if (whatsStoppingDraftEnd() != null) {
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
    private String endDraft(GenericInteractionCreateEvent event) {
        String blockingReason = whatsStoppingDraftEnd();
        if (blockingReason != null) {
            // If you got this accidentally, it means you called endDraft() instead of
            // tryEndDraft().
            // If there was an issue and someone used a slash command to force-end the
            // draft, that's fine.
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), "WARNING: Forcing the draft to end despite: " + blockingReason);
        }

        orchestrator.onDraftEnd(this);
        for (Draftable draftable : draftables) {
            draftable.onDraftEnd(this);
        }

        String blockingSetup = whatsStoppingSetup();
        if (blockingSetup != null) {
            MessageHelper.sendMessageToChannel(
                    game.getMainGameChannel(),
                    "The draft has ended. Some additional setup needs to happen before the game can start: "
                            + blockingSetup);
        } else {
            trySetupPlayers(event);
        }

        return null;
    }

    /**
     * Determine whether all lifecycle components are ready to end the picking of
     * draft
     * choices.
     *
     * @return Null if ready to end the draft, or a SPECIFIC message describing what
     *         is being waited on.
     */
    public String whatsStoppingDraftEnd() {
        for (Draftable d : draftables) {
            String reason = d.whatsStoppingDraftEnd(this);
            if (reason != null) {
                return reason;
            }
        }
        return orchestrator.whatsStoppingDraftEnd(this);
    }

    /**
     * Attempt to do player setup and start the game. If any component is not ready,
     * return without doing anything.
     *
     * @param event
     */
    public void trySetupPlayers(GenericInteractionCreateEvent event) {
        if (whatsStoppingSetup() != null) {
            return;
        }

        setupPlayers(event);
    }

    private void setupPlayers(GenericInteractionCreateEvent event) {
        String blockingReason = whatsStoppingSetup();
        if (blockingReason != null) {
            // If you got this accidentally, it means you called setupPlayers() instead of
            // trySetupPlayers().
            // If there was an issue and someone used a slash command to force-setup the
            // players, that's fine.
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), "WARNING: Forcing player setup despite: " + blockingReason);
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

        game.setPhaseOfGame("playerSetup");
        AddTileListService.finishSetup(game, event);
        ButtonHelper.updateMap(game, event);
    }

    public String whatsStoppingSetup() {
        for (Draftable d : draftables) {
            String result = d.whatsStoppingSetup(this);
            if (result != null) {
                return result;
            }
        }
        return orchestrator.whatsStoppingSetup(this);
    }

    /**
     * Check that all required fields are set and that all shared state is
     * consistent.
     */
    public void validateState() {
        // Errors that can't be fixed with slash commands, and should never happen
        super.validateState();
        if (game == null) {
            throw new IllegalStateException("Game not set");
        }
        if (draftables == null) {
            throw new IllegalStateException("Draftables not set");
        }

        MessageChannel issueChannel = game.getMainGameChannel();
        if (draftables.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    issueChannel, "Draft problem: Nothing to draft (try `/draft manage add_draftable`)");
        }
        if (orchestrator == null) {
            MessageHelper.sendMessageToChannel(
                    issueChannel, "Draft problem: No way to draft (try `/draft manage set_orchestrator`)");
        }
        if (playerStates.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    issueChannel, "Draft problem: No players in draft (try `/draft manage add_all_game_players`)");
        }
        if (orchestrator != null) {
            String validationError = orchestrator.validateState(this);
            if (validationError != null) {
                MessageHelper.sendMessageToChannel(
                        issueChannel, "Draft problem: The orchestrator reports: " + validationError);
            }
        }
        for (Draftable d : draftables) {
            String validationError = d.validateState(this);
            if (validationError != null) {
                MessageHelper.sendMessageToChannel(
                        issueChannel,
                        "Draft problem: The draftable " + d.getDisplayName() + " reports: " + validationError);
            }
        }

        // TODO:
        // All DraftChoices and Draftables conform to stated requirements for their
        // given strings,
        // e.g. getChoiceKey() is non-null, non-empty, lowercase alpha-numeric only,
        // etc.

        // TODO:
        // All DraftChoices across all Draftables have unique choice keys.
    }
}
