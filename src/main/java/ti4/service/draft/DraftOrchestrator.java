package ti4.service.draft;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.helpers.settingsFramework.menus.SettingsMenu;
import ti4.service.draft.DraftManager.CommandSource;

public abstract class DraftOrchestrator extends DraftLifecycleHooks {

    /**
     * The core function of a DraftOrchestrator: deliver and handle draft buttons.
     * In other words, this class is responsbile for the user experience.
     * This function should, for the current state of the draft, present information
     * and buttons in the appropriate channel(s) for players to make draft picks.
     * @param draftManager The draft manager holding the draft state.
     */
    public abstract void sendDraftButtons(DraftManager draftManager);

    // Interactions

    /**
     * Get the string prefix this orchestrator will use for buttons it is
     * providing and handling itself.
     * @return The button ID prefix.
     */
    public abstract String getButtonPrefix();

    /**
     * Handle a button interaction that corresponds to a custom button provided
     * by this orchestrator. This is identified using 'getButtonPrefix()'.
     * TODO: Switch to "command" concept, instead of assuming it was a button. Also provide command key maker.
     * @param event The event corresponding to the interaction.
     * @param draftManager The draft manager for this draft.
     * @param playerUserId The player which interacted with the button.
     * @param buttonId The button ID that was pressed.
     * @return null if the button was handled successfully, or an error message if not. Supports magic strings.
     */
    public abstract String handleCustomButtonPress(
            GenericInteractionCreateEvent event, DraftManager draftManager, String playerUserId, String buttonId);

    /**
     * Turn a draft choice into a draft pick. This function is called after the Draftable has validated
     * the choice can be a pick for this player, in this state. The orchestrator is expected to apply
     * its own validation logic (e.g. is it this player's turn to pick?) and then apply the choice
     * to the draft state. In addition, the orchestrator is responsible for teeing up whatever happens next.
     * This could be sending new buttons, making deterministic picks, or a simple ping.
     * NOTE: The DraftManager is responsible for the phase of the draft, and may end the draft after this
     * function returns.
     * @param event The event corresponding to the interaction.
     * @param draftManager The draft manager for this draft.
     * @param playerUserId The player making the pick.
     * @param choice The choice key of the desired pick.
     * @param source The source of the command. May indicate that this choice is being applied automatically, which
     * could suggest different handling.
     * @return null if the choice was applied successfully, or an error message if not. Supports magic strings.
     */
    public abstract String applyDraftChoice(
            GenericInteractionCreateEvent event,
            DraftManager draftManager,
            String playerUserId,
            DraftChoice choice,
            CommandSource source);

    // Persistence

    public static final String SAVE_SEPARATOR = ",";

    /**
     * Save the state of this orchestrator to a string.
     * NOTE: This does NOT include per-player state, which is handled separately.
     * @return The saved state as a string.
     */
    public abstract String save();

    /**
     * Load the state of this orchestrator from a string.
     * NOTE: This does NOT include per-player state, which is handled separately.
     * @param data The saved state as a string.
     */
    public abstract void load(String data);

    /**
     * Save this player-specific substate of the orchestrator to a string.
     * @param state The player-specific substate to save.
     * @return The saved state as a string.
     */
    public abstract String savePlayerState(OrchestratorState state);

    /**
     * Load this player-specific substate of the orchestrator from a string.
     * @param data The saved state as a string.
     * @return The loaded player-specific substate.
     */
    public abstract OrchestratorState loadPlayerState(String data);

    /**
     * Validate the current state of the draft for this orchestrator.
     * @param draftManager The draft manager to validate against.
     * @return null if valid, or an error message if invalid.
     */
    public abstract String validateState(DraftManager draftManager);

    // Setup

    /**
     * Draft orchestrators should NOT keep per-player state themselves, in
     * case the players change or get swapped. Instead, PlayerDraftState has
     * a generic field for orchestrator state. This function should initialize
     * it for an arbitrary player.
     * @param draftManager The draft manager to initialize player states for.
     */
    public abstract void initializePlayerStates(DraftPlayerManager draftManager);

    public String applySetupMenuChoices(GenericInteractionCreateEvent event, SettingsMenu menu) {
        // Do nothing by default
        return null;
    }
}
