package ti4.service.draft;

public abstract class DraftLifecycleHooks {
    // Do any work that is needed before the draft starts.
    public void preDraftStart(DraftManager draftManager) {}

    // Return true if the draftable has completed all work needed to start the draft
    public boolean canStartDraft(DraftManager draftManager) {
        return true;
    }

    // Return true if players have made enough choices that this Draftable can
    // finalize setup for whatever it controls.
    // Ex. If a FactionDraftable has 5 choices for 4 players, this returns true once
    // each player has 1 of FactionDraftable's Draft Choices in their state.
    public abstract boolean canEndDraft(DraftManager draftManager);

    // Inform the draftable that the draft is over, and it should begin any
    // post-draft work it needs to do.
    // Ex. Send the Keleres player a DM with buttons to choose their flavor.
    public void onDraftEnd(DraftManager draftManager) {}

    // Return true if all post-draft work is finished, and the player can now be set
    // up. Ex. has the Keleres player chosen a flavor.
    public boolean canSetupPlayers(DraftManager draftManager) {
        return true;
    }

    // Perform any work needed to set up the player in the game. Ex. assign the
    // chosen faction to the player.
    // Can also do custom setup work, e.g. add starting trade goods, etc.
    public abstract void setupPlayer(
            DraftManager draftManager, String playerUserId, PlayerSetupService.PlayerSetupState playerSetupState);
}
