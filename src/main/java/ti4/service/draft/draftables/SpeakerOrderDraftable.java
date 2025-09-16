package ti4.service.draft.draftables;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.helpers.MapTemplateHelper;
import ti4.helpers.StringHelper;
import ti4.helpers.omega_phase.PriorityTrackHelper.PriorityTrackMode;
import ti4.map.Player;
import ti4.service.draft.DraftChoice;
import ti4.service.draft.DraftManager;
import ti4.service.draft.Draftable;
import ti4.service.draft.DraftableType;
import ti4.service.draft.PlayerDraftState;
import ti4.service.draft.PlayerSetupService.PlayerSetupState;
import ti4.service.emoji.MiltyDraftEmojis;

public class SpeakerOrderDraftable extends Draftable {

    private int numPlayers;

    public void initialize(int numPlayers) {
        this.numPlayers = numPlayers;
    }

    @Override
    public DraftableType getType() {
        return DraftableType.of("SpeakerOrder");
    }

    @Override
    public List<DraftChoice> getAllDraftChoices() {
        List<DraftChoice> choices = new ArrayList<>();
        for (int i = 1; i <= numPlayers; i++) {
            String choiceKey = "pick" + i;
            String buttonEmoji = MiltyDraftEmojis.getSpeakerPickEmoji(i).toString();
            String displayName = StringHelper.ordinal(i) + " Pick";
            choices.add(new DraftChoice(
                    getType(), choiceKey, makeChoiceButton(choiceKey, null, buttonEmoji), displayName, buttonEmoji));
        }
        return choices;
    }

    @Override
    public String getCommandKey() {
        return "pickorder";
    }

    @Override
    public String handleCustomCommand(
            GenericInteractionCreateEvent event, DraftManager draftManager, String playerUserId, String buttonId) {

        return "Unknown button press: " + buttonId;
    }

    @Override
    public String isValidDraftChoice(DraftManager draftManager, String playerUserId, DraftChoice choice) {
        if (!CommonDraftableValidators.hasRemainingChoices(draftManager, playerUserId, getType(), 1)) {
            return "You already have a Speaker order pick!";
        }
        List<String> choiceKeys = IntStream.rangeClosed(1, numPlayers)
                .boxed()
                .map(Object::toString)
                .collect(Collectors.toList());
        if (!CommonDraftableValidators.isChoiceKeyInList(choice, choiceKeys)) {
            return "That speaker order pick is not valid!";
        }

        return null;
    }

    @Override
    public DraftChoice getNothingPickedChoice() {
        return new DraftChoice(
                getType(),
                null,
                null,
                "No speaker position",
                MiltyDraftEmojis.getSpeakerPickEmoji(-1).toString());
    }

    @Override
    public String save() {
        return "" + numPlayers;
    }

    @Override
    public void load(String data) {
        numPlayers = Integer.parseInt(data);
    }

    @Override
    public String validateState(DraftManager draftManager) {
        if (numPlayers < 1) {
            return "Number of speaker positions must be at least 1, but is: " + numPlayers;
        }
        int numDraftPlayers = draftManager.getPlayerStates().size();
        if (numPlayers < numDraftPlayers) {
            return "Number of speaker positions (" + numPlayers + ") is less than number of players drafting ("
                    + numDraftPlayers + ")";
        }

        // Ensure no two players have picked the same speaker position.
        List<DraftChoice> speakerChoices = draftManager.getAllPicksOfType(getType());
        Set<String> chosenSpeakerPositions = new HashSet<>();
        for (DraftChoice choice : speakerChoices) {
            if (chosenSpeakerPositions.contains(choice.getChoiceKey())) {
                return "Multiple players have chosen speaker position " + choice.getChoiceKey();
            }
            chosenSpeakerPositions.add(choice.getChoiceKey());
        }
        return null;
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
        int speakerNum = Integer.parseInt(speakerOrder);
        playerSetupState.setSetSpeaker(speakerNum == 1);

        if (shouldAlsoSetSeat(draftManager)) {
            String homeTilePosition = MapTemplateHelper.getPlayerHomeSystemLocation(
                    speakerNum, draftManager.getGame().getMapTemplateID());
            playerSetupState.setPositionHS(homeTilePosition);
        }

        return (Player p) -> {
            if (!shouldAlsoSetSeat(draftManager)) {
                draftManager.getGame().setPriorityTrackMode(PriorityTrackMode.THIS_ROUND_ONLY);
                draftManager.getGame().getPlayer(playerUserId).setPriorityPosition(speakerNum);
            }
        };
    }

    private boolean shouldAlsoSetSeat(DraftManager draftManager) {
        return !draftManager.getDraftables().stream().anyMatch(d -> d instanceof SeatDraftable);
    }
}
