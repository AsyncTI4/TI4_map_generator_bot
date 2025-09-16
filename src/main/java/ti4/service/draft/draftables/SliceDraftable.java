package ti4.service.draft.draftables;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.utils.FileUpload;
import ti4.map.Player;
import ti4.service.draft.DraftChoice;
import ti4.service.draft.DraftManager;
import ti4.service.draft.DraftSliceHelper;
import ti4.service.draft.Draftable;
import ti4.service.draft.DraftableType;
import ti4.service.draft.PlayerSetupService.PlayerSetupState;
import ti4.service.draft.SliceImageGeneratorService;
import ti4.service.emoji.MiltyDraftEmojis;
import ti4.service.milty.MiltyDraftSlice;

public class SliceDraftable extends Draftable {

    private List<MiltyDraftSlice> slices;

    public void initialize(List<MiltyDraftSlice> slices) {
        this.slices = slices;
    }

    public MiltyDraftSlice getSliceByName(String name) {
        for (MiltyDraftSlice slice : slices) {
            if (slice.getName().equals(name)) {
                return slice;
            }
        }
        return null;
    }

    public List<MiltyDraftSlice> getDraftSlices() {
        return slices;
    }

    public static final DraftableType TYPE = DraftableType.of("Slice");

    @Override
    public DraftableType getType() {
        return TYPE;
    }

    @Override
    public List<DraftChoice> getAllDraftChoices() {
        List<DraftChoice> choices = new ArrayList<>();
        for (MiltyDraftSlice slice : slices) {
            String choiceKey = slice.getName();
            String buttonEmoji = MiltyDraftEmojis.getMiltyDraftEmoji(choiceKey).toString();
            String displayName = "Slice " + slice.getName();
            choices.add(new DraftChoice(getType(), choiceKey, makeChoiceButton(choiceKey, null, buttonEmoji), displayName, buttonEmoji));
        }
        return choices;
    }

    @Override
    public String handleCustomCommand(
            GenericInteractionCreateEvent event, DraftManager draftManager, String playerUserId, String buttonId) {

        return "Unknown button press: " + buttonId;
    }

    @Override
    public String isValidDraftChoice(DraftManager draftManager, String playerUserId, DraftChoice choice) {
        if (!CommonDraftableValidators.isChoiceKeyInList(
                choice, slices.stream().map(MiltyDraftSlice::getName).toList())) {
            return "That slice is not recognized.";
        }
        if (!CommonDraftableValidators.hasRemainingChoices(
                draftManager, playerUserId, getType(), 1)) {
            return "You have already picked your slice.";
        }

        return null;
    }

    @Override
    public DraftChoice getNothingPickedChoice() {
        return new DraftChoice(getType(), null, null, "No slice picked", MiltyDraftEmojis.getMiltyDraftEmoji(null).toString());
    }

    @Override
    public FileUpload generateSummaryImage(DraftManager draftManager, String uniqueKey, List<String> restrictChoiceKeys) {
        return SliceImageGeneratorService.tryGenerateImage(draftManager, uniqueKey, restrictChoiceKeys);
    }

    @Override
    public String save() {
        return String.join(";", slices.stream().map(MiltyDraftSlice::ttsString).toList());
    }

    @Override
    public void load(String data) {
        slices = new ArrayList<>(DraftSliceHelper.parseSlicesFromString(data));
    }

    @Override
    public String validateState(DraftManager draftManager) {
        int numPlayers = draftManager.getPlayerStates().size();
        if (slices.size() < numPlayers) {
            return "Number of slices (" + slices.size() + ") is less than number of players (" + numPlayers + ")";
        }

        // Ensure no two players have picked the same slice.
        List<DraftChoice> sliceChoices = draftManager.getAllPicksOfType(getType());
        Set<String> chosenSlices = new HashSet<>();
        for (DraftChoice choice : sliceChoices) {
            if (chosenSlices.contains(choice.getChoiceKey())) {
                return "Multiple players have chosen slice " + choice.getChoiceKey();
            }
            chosenSlices.add(choice.getChoiceKey());
        }
        return null;
    }

    @Override
    public Consumer<Player> setupPlayer(DraftManager draftManager, String playerUserId, PlayerSetupState playerSetupState) {
        // Map is built as a side effect of slice drafting.
        return null;
    }
}
