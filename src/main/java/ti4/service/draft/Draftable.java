package ti4.service.draft;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.utils.FileUpload;
import ti4.buttons.Buttons;

public abstract class Draftable extends DraftLifecycleHooks {
    /**
     * Get the type of draftable, e.g. "Speaker", "Slice", "Faction".
     * @return An object representing the type of draftable. It's a glorified string.
     */
    public abstract DraftableType getType();
    /**
     * Get all the draft choices that are available to this specific draft.
     * For example, if this is a Faction draftable, it would only return the 7 factions selected as options when this draft was created.
     * @return A list of all draft choices, with unique choiceKey values.
     */
    public abstract List<DraftChoice> getAllDraftChoices();

    /**
     * Get a specific, known draft choice by its key.
     * @param choiceKey The unique key of the draft choice to get.
     * @return The draft choice with the given key, or null if not found.
     */
    public final DraftChoice getDraftChoice(String choiceKey) {
        List<DraftChoice> allChoices = getAllDraftChoices();
        for (DraftChoice choice : allChoices) {
            if (choice.getChoiceKey().equals(choiceKey)) {
                return choice;
            }
        }
        return null;
    }

    /**
     * Get all the draft choices that are available to this draft, just they keys.
     * @return A list of all draft choice keys.
     */
    public final Set<String> getAllDraftChoiceKeys() {
        List<DraftChoice> allChoices = getAllDraftChoices();
        return allChoices.stream().map(DraftChoice::getChoiceKey).collect(Collectors.toSet());
    }

    // Interaction info

    /**
     * Get the string used as part of button IDs (to route them to this draftable),
     * and used as a sub-command for the "/draft" command.
     * @return A lowercase alphabetical string.
     */
    public String getDraftableCommandKey() {
        return getType().toString().toLowerCase().replaceAll("[^a-z]", "");
    }
    /**
     * Make a button ID which the draft service will return to the draftable.
     * @return A button ID which will be picked up by the DraftButtonService
     */
    public final String makeButtonId(String actionKey) {
        return DraftButtonService.DRAFT_BUTTON_SERVICE_PREFIX + makeCommandKey(actionKey);
    }
    /**
     * Make an action/choice command key, which routes to this draftable with a specific choice or action.
     * @param actionKey The action or choice this draftable will recognize.
     * @return A string which the DraftManager will route to this draftable, specifying the actionKey.
     */
    public final String makeCommandKey(String actionKey) {
        return getDraftableCommandKey() + "_" + actionKey;
    }
    /**
     * Make a button for a given draft choice, which will be automatically routed to make this choice.
     * @return A button to be used to make the given choice.
     */
    protected Button makeChoiceButton(String choiceKey, String buttonText, String emoji) {
        if (buttonText == null && emoji == null) {
            throw new IllegalArgumentException("Must provide at least buttonText or emoji");
        }
        if (buttonText != null) {
            return Buttons.gray(makeButtonId(choiceKey), buttonText, emoji);
        } else {
            return Buttons.green(makeButtonId(choiceKey), null, emoji);
        }
    }

    /**
     * Draftables may provide buttons that go alongside their choices, but which are not
     * themselves choices. These buttons should be created with makeButtonId, so they
     * can be handled by the draftable.
     * Ex. FactionDraftable provides buttons like "Picked faction info" alongside its choices.
     * @param restrictChoiceKeys If non-null, only include buttons relevant to these choice keys. If null, include all buttons. Useful for private buttons showing only a player's own choices.
     * @return A list of custom buttons to display alongside the draft choices.
     */
    public List<Button> getCustomChoiceButtons(List<String> restrictChoiceKeys) {
        return List.of();
    }
    /**
     * Invoked when a custom button is pressed or a custom slash-command is used. NOT when a draft choice is made.
     * @param event The interaction event, either from a slash-command or a button press.
     * @param draftManager The draft manager for the draft; also contains the Game object.
     * @param playerUserId The user ID of the player who pressed the button or used the command.
     * @param commandKey The command key associated with the button or command.
     * @return An error message if the command is invalid, null if successful. Can also return DraftButtonService magic strings if successful, which slash-handlers ignore but can affect the button or its message.
     */
    public abstract String handleCustomCommand(
            GenericInteractionCreateEvent event, DraftManager draftManager, String playerUserId, String commandKey);
    /**
     * Check whether a draft choice is allowed, and return an explanation if not.
     * @param draftManager The draft manager for the draft; also contains the Game object.
     * @param playerUserId The user ID of the player who made the choice.
     * @param choice The draft choice to validate.
     * @return An error message if the choice is invalid, null if valid. No magic string support.
     */
    public String isValidDraftChoice(DraftManager draftManager, String playerUserId, DraftChoice choice) {
        Set<String> allChoiceKeys = getAllDraftChoiceKeys();
        if (!allChoiceKeys.contains(choice.getChoiceKey())) {
            return "The choiceKey " + choice.getChoiceKey() + " is not valid for draftable type " + getType();
        }
        return null;
    }

    /**
     * Perform any side effects of a draft choice being made, such as updating the map.
     * @param event The interaction event, either from a slash-command or a button press.
     * @param draftManager The draft manager for the draft; also contains the Game object.
     * @param playerUserId The user ID of the player who made the choice.
     * @param choice The draft choice that was made.
     */
    public void postApplyDraftPick(
            GenericInteractionCreateEvent event, DraftManager draftManager, String playerUserId, DraftChoice choice) {
        // Empty
    }
    /**
     * To support automatic picking when choices are few, this gives the option for a draftable to provide
     * a set of DraftChoices that represent the only legal choices for that player. Note that some orchestrators
     * support picking multiple choices at once, so this returns a list of DraftChoices.
     * @param draftManager The draft manager for the draft; also contains the Game object.
     * @param playerUserId The user ID of the player to pick for.
     * @param numberOfSimultaneousPicks The number of choices the player can at once. Usually 1, but can be more (e.g. a player picking twice at the end of a draft-snake)
     * @return null if the player could make more than exactly one set of choices with their total simultaneous picks. Or just null to disable automatic picking.
     *         Otherwise, return the set of DraftChoices that the player must pick from this draftable. For most draftables, this will be a list of 1 thing (e.g. the only remaining slice).
     *         If there are no remaining choices left to the player, return an empty list.
     *         If this draftable supports multiple picks per player (e.g. franken faction tech), override this to be able to return up to numberOfSimultaneousPicks choices.
     */
    public List<DraftChoice> getDeterministicPick(
            DraftManager draftManager, String playerUserId, int numberOfSimultaneousPicks) {
        return null;
    }

    // Rendering info

    /**
     * Get a display name for this draftable, used in the draft summary and other places.
     * @return A human-friendly name for this draftable. Should also be friendly to formatting, such as bold+underline+uppercase.
     */
    public String getDisplayName() {
        return getType().toString();
    }
    /**
     * For rendering, get a DraftChoice appropriate for a player that hasn't made picks from this draftable.
     * Ex. The FactionDraftable would return a DraftChoice with the "random good dog" emoji and text like "No faction picked".
     * @return An (invalid) "no choice made" DraftChoice from which text and rendering info can be pulled. This probably shouldn't be accepted as a real choice.
     */
    public abstract DraftChoice getNothingPickedChoice();
    /**
     * Generate an image representing the current state of this draftable. Orchestrators can call this, probably after setup and
     * after each pick.
     * Ex. The SliceDraftable uses this to generate the slice image and updates it when a player picks a slice.
     * @param draftManager The draft manager for the draft; also contains the Game object.
     * @param uniqueKey A unique key which should be used in the filename, so that the attachment can be located later.
     * @param restrictChoiceKeys If non-null, only include these choice keys in the image. If null, include all choices. Useful for private images showing only a player's own choices.
     * @return An image file upload representing the current state of this draftable, or null if no image is created.
     */
    public FileUpload generateSummaryImage(
            DraftManager draftManager, String uniqueKey, List<String> restrictChoiceKeys) {
        return null;
    }

    // Persistence

    /**
     * Separator used in save/load strings.
     */
    public static final String SAVE_SEPARATOR = ",";
    /**
     * Serialize any state that this draftable needs to persist.
     * Ex. the number of seats in a SeatDraftable.
     * @return A single-line string representing the state of this draftable.
     */
    public abstract String save();
    /**
     * Restore the state of this specific instance using the save string.
     * @param data A string previously returned by save().
     */
    public abstract void load(String data);
    /**
     * Validate that the state of this draftable is functional. Call after initialization and after loading.
     * NOTE: Any error here should be fixable by players/bothelpers using slash-commands. Ensure they're provided!
     * @param draftManager The draft manager for the draft; also contains the Game object.
     * @return An error message if the state is invalid, or null if valid. No magic string support.
     */
    public abstract String validateState(DraftManager draftManager);
}
