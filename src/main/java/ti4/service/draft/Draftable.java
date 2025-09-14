package ti4.service.draft;

import java.util.List;

import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.utils.FileUpload;
import ti4.service.draft.draftables.CommonDraftableValidators;

public abstract class Draftable extends DraftLifecycleHooks {
    // Get a String representing the type of draftable, e.g. "Speaker", "Slice", "Faction".
    public abstract DraftableType getType();
    // Get the draft choices that are available to this draft. This list DOES include
    // already-chosen options, but does NOT include options that are not part of this draft.
    // Ex. If a FactionDraftable has 5 options, and all but Xxcha have been chosen, then this returns all 5 options.
    public abstract List<DraftChoice> getAllDraftChoices();
    /**
     * Get the exact number of choices each player should make for this Draftable.
     * @return The number of choices each player should make.
     */
    public abstract int getNumChoicesPerPlayer();

    // Interaction info

    // Get the prefix this handler uses with all draft choice and other buttons.
    public abstract String getButtonPrefix();
    // Get the list of buttons representing not-draft-choice actions, e.g. "More Info".
    public List<Button> getCustomButtons() {
        return List.of();
    }
    // Handle a non-draft-choice button press. Returns an error string if the button press is invalid, or null if it was handled.
    public abstract String handleCustomButtonPress(GenericInteractionCreateEvent event, DraftManager draftManager, String playerUserId, String buttonId);
    // Validate a draft choice before it's applied.
    public abstract String isValidDraftChoice(DraftManager draftManager, String playerUserId, DraftChoice choice);
    // Apply a draft choice to the draft state. Can also have side-effects on the draft state.
    public void draftChoiceSideEffects(GenericInteractionCreateEvent event, DraftManager draftManager, String playerUserId, DraftChoice choice) {
        // Empty
    }

    // Rendering info

    // Get a header string for the buttons message, which is also used to find the message for edits.
    public abstract String getChoiceHeader();
    // Whether the draftable provides emoji / strings which can be used inline with other summary text.
    public abstract boolean hasInlineSummary();
    // Get the default inline summary text when a player hasn't made a selection.
    public abstract String getDefaultInlineSummary();
    // Generate and upload the draft summary image for this Draftable. Return null if no image is generated.
    public FileUpload generateDraftImage(DraftManager draftManager) {
        return null;
    }

    // Persistence

    public abstract String save();
    public abstract void load(String data);
    public abstract void validateState(DraftManager draftManager);

    // Common Lifecycle Logic
    @Override
    public boolean canEndDraft(DraftManager draftManager) {
        for (String playerUserId : draftManager.getPlayerStates().keySet()) {
            if (CommonDraftableValidators.hasRemainingChoices(draftManager, playerUserId, getType(),
                    getNumChoicesPerPlayer())) {
                return false;
            }
        }
        return true;
    }
}
