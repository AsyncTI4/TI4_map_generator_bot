package ti4.service.draft.draftables;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.utils.FileUpload;
import ti4.service.draft.DraftChoice;
import ti4.service.draft.DraftManager;
import ti4.service.draft.DraftSliceHelper;
import ti4.service.draft.Draftable;
import ti4.service.draft.DraftableType;
import ti4.service.draft.PartialMapService;
import ti4.service.draft.PlayerDraftState;
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

    public static final DraftableType TYPE = new DraftableType("Slice");

    @Override
    public DraftableType getType() {
        return TYPE;
    }

    @Override
    public List<DraftChoice> getAllDraftChoices() {
        List<DraftChoice> choices = new ArrayList<>();
        for (MiltyDraftSlice slice : slices) {
            String choiceKey = slice.getName();
            String buttonText = MiltyDraftEmojis.getMiltyDraftEmoji(choiceKey).toString();
            String simpleName = slice.getName();
            String inlineSummary =
                    MiltyDraftEmojis.getMiltyDraftEmoji(choiceKey).toString();
            String buttonSuffix = choiceKey;
            choices.add(new DraftChoice(getType(), choiceKey, buttonText, simpleName, inlineSummary, buttonSuffix));
        }
        return choices;
    }

    @Override
    public int getNumChoicesPerPlayer() {
        return 1;
    }

    @Override
    public String getButtonPrefix() {
        return "slice_";
    }

    @Override
    public String handleCustomButtonPress(
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
                draftManager, playerUserId, getType(), getNumChoicesPerPlayer())) {
            return "You have already picked your slice.";
        }

        return null;
    }

    @Override
    public void draftChoiceSideEffects(
            GenericInteractionCreateEvent event, DraftManager draftManager, String playerUserId, DraftChoice choice) {
        PartialMapService.tryUpdateMap(event, draftManager);
    }

    @Override
    public String getChoiceHeader() {
        return "**__Slices:__**";
    }

    @Override
    public boolean hasInlineSummary() {
        return true;
    }

    @Override
    public String getDefaultInlineSummary() {
        return MiltyDraftEmojis.getMiltyDraftEmoji(null).toString();
    }

    @Override
    public FileUpload generateDraftImage(DraftManager draftManager) {
        return SliceImageGeneratorService.tryGenerateImage(draftManager);
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
    public void validateState(DraftManager draftManager) {
        int numPlayers = draftManager.getPlayerStates().size();
        if (slices.size() < numPlayers) {
            throw new IllegalStateException(
                    "Number of slices (" + slices.size() + ") is less than number of players (" + numPlayers + ")");
        }

        // Ensure no two players have picked the same slice.
        List<DraftChoice> sliceChoices = draftManager.getAllPicksOfType(getType());
        Set<String> chosenSlices = new HashSet<>();
        for (DraftChoice choice : sliceChoices) {
            if (chosenSlices.contains(choice.getChoiceKey())) {
                throw new IllegalStateException("Multiple players have chosen slice " + choice.getChoiceKey());
            }
            chosenSlices.add(choice.getChoiceKey());
        }
    }

    @Override
    public void setupPlayer(DraftManager draftManager, String playerUserId, PlayerSetupState playerSetupState) {
        PlayerDraftState pState = draftManager.getPlayerStates().get(playerUserId);
        if (!pState.getPicks().containsKey(getType())
                || pState.getPicks().get(getType()).isEmpty()) {
            throw new IllegalStateException("Player " + playerUserId + " has not picked a slice");
        }

        // Do nothing; slice tiles get placed during choice side effects
    }
}
