package ti4.service.draft.draftables;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import lombok.Getter;
import lombok.Setter;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.utils.FileUpload;
import ti4.helpers.MapTemplateHelper;
import ti4.helpers.StringHelper;
import ti4.helpers.omega_phase.PriorityTrackHelper;
import ti4.helpers.omega_phase.PriorityTrackHelper.PriorityTrackMode;
import ti4.helpers.settingsFramework.menus.DraftSystemSettings;
import ti4.helpers.settingsFramework.menus.SettingsMenu;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.model.MapTemplateModel;
import ti4.service.draft.DraftChoice;
import ti4.service.draft.DraftManager;
import ti4.service.draft.DraftableType;
import ti4.service.draft.NucleusImageGeneratorService;
import ti4.service.draft.PlayerDraftState;
import ti4.service.draft.PlayerSetupService.PlayerSetupState;
import ti4.service.emoji.MiltyDraftEmojis;

public class SpeakerOrderDraftable extends SinglePickDraftable {

    @Getter
    @Setter
    private int numPicks;

    public void initialize(int numPlayers) {
        this.numPicks = numPlayers;
    }

    public static Integer getSpeakerOrderFromChoiceKey(String choiceKey) {
        if (choiceKey == null) return null;
        if (!choiceKey.startsWith("pick")) return null;
        return Integer.parseInt(choiceKey.substring(4));
    }

    public static final DraftableType TYPE = DraftableType.of("SpeakerOrder");

    @Override
    public DraftableType getType() {
        return TYPE;
    }

    @Override
    public List<DraftChoice> getAllDraftChoices() {
        List<DraftChoice> choices = new ArrayList<>();
        for (int i = 1; i <= numPicks; i++) {
            String choiceKey = "pick" + i;
            String buttonEmoji = MiltyDraftEmojis.getSpeakerPickEmoji(i).toString();
            String displayName = StringHelper.ordinal(i) + " Pick";
            String unformattedName = StringHelper.ordinal(i) + " Pick";
            choices.add(new DraftChoice(
                    getType(),
                    choiceKey,
                    makeChoiceButton(choiceKey, null, buttonEmoji),
                    displayName,
                    unformattedName,
                    buttonEmoji));
        }
        return choices;
    }

    @Override
    public String getDraftableCommandKey() {
        return "pickorder";
    }

    @Override
    public String handleCustomCommand(
            GenericInteractionCreateEvent event, DraftManager draftManager, String playerUserId, String commandKey) {

        return "Unknown command: " + commandKey;
    }

    @Override
    public String getDisplayName() {
        return "Speaker Order";
    }

    @Override
    public DraftChoice getNothingPickedChoice() {
        return new DraftChoice(
                getType(),
                null,
                null,
                "No speaker position",
                "No speaker position",
                MiltyDraftEmojis.getSpeakerPickEmoji(-1).toString());
    }

    @Override
    public FileUpload generateSummaryImage(
            DraftManager draftManager, String uniqueKey, List<String> restrictChoiceKeys) {

        // If Speaker Order determines seating, also generate a summary image for a Nucleus template
        if (shouldAlsoSetSeat(draftManager) && draftManager.getGame().getMapTemplateID() != null) {
            MapTemplateModel mapTemplate =
                    Mapper.getMapTemplate(draftManager.getGame().getMapTemplateID());
            if (mapTemplate != null && mapTemplate.isNucleusTemplate()) {
                return NucleusImageGeneratorService.tryGenerateImage(draftManager, uniqueKey, restrictChoiceKeys);
            }
        }

        return null;
    }

    @Override
    public String save() {
        return "" + numPicks;
    }

    @Override
    public void load(String data) {
        numPicks = Integer.parseInt(data);
    }

    @Override
    public String validateState(DraftManager draftManager) {
        if (numPicks < 1) {
            return "Number of speaker positions must be at least 1, but is: " + numPicks
                    + ". Fix it with `/draft pickorder set_num_picks`.";
        }
        int numDraftPlayers = draftManager.getPlayerStates().size();
        if (numPicks < numDraftPlayers) {
            return "Number of speaker positions (" + numPicks + ") is less than number of players drafting ("
                    + numDraftPlayers + "). Fix it with `/draft pickorder set_num_picks`.";
        }
        return super.validateState(draftManager);
    }

    @Override
    public Consumer<Player> setupPlayer(
            DraftManager draftManager, String playerUserId, PlayerSetupState playerSetupState) {
        PlayerDraftState pState = draftManager.getPlayerStates().get(playerUserId);
        if (!pState.getPicks().containsKey(getType())
                || pState.getPicks().get(getType()).isEmpty()) {
            throw new IllegalStateException("Player " + playerUserId + " has not picked a speaker order");
        }

        String speakerOrder = pState.getPicks().get(getType()).get(0).getChoiceKey();
        Integer speakerNum = getSpeakerOrderFromChoiceKey(speakerOrder);
        if (speakerNum == null) {
            throw new IllegalStateException(
                    "Player " + playerUserId + " has an invalid speaker order choice key: " + speakerOrder);
        }

        playerSetupState.setSetSpeaker(speakerNum == 1);

        if (shouldAlsoSetSeat(draftManager)) {
            String homeTilePosition = MapTemplateHelper.getPlayerHomeSystemLocation(
                    speakerNum, draftManager.getGame().getMapTemplateID());
            playerSetupState.setPositionHS(homeTilePosition);
        }

        return (Player p) -> {
            if (!shouldAlsoSetSeat(draftManager)) {
                draftManager.getGame().setPriorityTrackMode(PriorityTrackMode.THIS_ROUND_ONLY);
                PriorityTrackHelper.AssignPlayerToPriority(draftManager.getGame(), p, speakerNum);
            }
        };
    }

    @Override
    public String applySetupMenuChoices(GenericInteractionCreateEvent event, SettingsMenu menu) {
        if (menu == null || !(menu instanceof DraftSystemSettings)) {
            return "Error: Could not find parent draft system settings.";
        }
        DraftSystemSettings draftSystemSettings = (DraftSystemSettings) menu;
        Game game = draftSystemSettings.getGame();
        if (game == null) {
            return "Error: Could not find game instance.";
        }
        initialize(draftSystemSettings.getPlayerUserIds().size());
        return null;
    }

    private boolean shouldAlsoSetSeat(DraftManager draftManager) {
        return !draftManager.getDraftables().stream().anyMatch(d -> d instanceof SeatDraftable);
    }
}
