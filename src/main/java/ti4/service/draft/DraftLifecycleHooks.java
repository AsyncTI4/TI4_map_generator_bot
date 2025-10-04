package ti4.service.draft;

import java.util.function.Consumer;
import ti4.map.Player;

/**
 * The draft lifecycle goes through several stages. These hooks
 * should allow draft components to do extra work and control the flow
 * as needed, hopefully without being overbearing. More hooks should
 * be added only as really needed, and with default noop implementations.
 *
 * The draft manager is responsible for checking and progressing through the lifecycle.
 *
 * Draft stages:
 * 1. Initialization - Draftables generate their choices, etc.
 * 2. Drafting - Players make choices from draftables.
 *   - Moving on from this stage is blocked until all components return 'null' from 'whatsStoppingDraftEnd'.
 * 3. Post-draft work - Draftables can do extra work, such as asking players to select a Keleres flavor.
 *   - Moving on from this stage is blocked until all components return 'null' from 'whatsStoppingSetup'.
 * 4. Player setup - Draftables apply their DraftChoices to players that picked them.
 */
public abstract class DraftLifecycleHooks {
    /**
     * Check whether this component is ready to start the draft. Draftables should generally
     * return a specific error message until they are completely ready to start. Then they
     * should return null.
     * @param draftManager The draft manager for the draft; also contains the Game object.
     * @return Null if ready to start the draft, or a SPECIFIC message describing what is being waited on.
     */
    public String whatsStoppingDraftStart(DraftManager draftManager) {
        return null;
    }

    /**
     * Check whether this component is ready to end the draft. Draftables should generally
     * return a blocking reason until all players have picked one of its choices.
     * This is called after every pick automatically.
     * @param draftManager The draft manager for the draft; also contains the Game object.
     * @return Null if ready to end the draft, or a SPECIFIC message describing what is being waited on.
     */
    public abstract String whatsStoppingDraftEnd(DraftManager draftManager);

    /**
     * Called once when the draft ends, after canEndDraft returns true for all components.
     * This is where the draftable can begin any post-draft work.
     * Ex. The MantisTileDraftable runs a map building sequence in the main game channel.
     * @param draftManager The draft manager for the draft; also contains the Game object.
     */
    public void onDraftEnd(DraftManager draftManager) {}

    /**
     * Check whether this draftable is ready to set up the players. Draftables which send buttons in
     * onDraftEnd may need to wait for player interaction. Anything which blocks this method from
     * returning null MUST call the DraftManager's trySetupPlayers() method when it is resolved.
     * @param draftManager The draft manager for the draft; also contains the Game object.
     * @return Null if ready to set up players, or a SPECIFIC message describing what is being waited on.
     */
    public String whatsStoppingSetup(DraftManager draftManager) {
        return null;
    }

    /**
     * Apply this draftable's choices to the given player, modifying the setup object as needed.
     * @param draftManager The draft manager for the draft; also contains the Game object.
     * @param playerUserId The user ID of the player to set up.
     * @param playerSetupState The setup state object for the player.
     * @return An optional consumer which will be invoked on this same player after setup, to do any additional work.
     */
    public abstract Consumer<Player> setupPlayer(
            DraftManager draftManager, String playerUserId, PlayerSetupService.PlayerSetupState playerSetupState);
}
