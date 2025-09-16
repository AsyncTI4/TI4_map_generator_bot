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
import ti4.image.Mapper;
import ti4.map.Player;
import ti4.model.MapTemplateModel;
import ti4.service.draft.DraftChoice;
import ti4.service.draft.DraftManager;
import ti4.service.draft.Draftable;
import ti4.service.draft.DraftableType;
import ti4.service.draft.PlayerDraftState;
import ti4.service.draft.PlayerSetupService.PlayerSetupState;
import ti4.service.emoji.MiltyDraftEmojis;

public class SeatDraftable extends Draftable {

    private int numSeats;

    public void initialize(int numSeats) {
        this.numSeats = numSeats;
    }

    @Override
    public DraftableType getType() {
        return DraftableType.of("Seat");
    }

    @Override
    public List<DraftChoice> getAllDraftChoices() {
        List<DraftChoice> choices = new ArrayList<>();
        for (int i = 1; i <= numSeats; i++) {
            String choiceKey = "seat" + i;
            String buttonEmoji = MiltyDraftEmojis.getSpeakerPickEmoji(i).toString();
            String displayName = "Seat " + i;
            choices.add(new DraftChoice(
                    getType(), choiceKey, makeChoiceButton(choiceKey, null, buttonEmoji), displayName, buttonEmoji));
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
        if (!CommonDraftableValidators.hasRemainingChoices(draftManager, playerUserId, getType(), 1)) {
            return "You already have a Seat pick!";
        }
        List<String> choiceKeys =
                IntStream.rangeClosed(1, numSeats).boxed().map(Object::toString).collect(Collectors.toList());
        if (!CommonDraftableValidators.isChoiceKeyInList(choice, choiceKeys)) {
            return "That seat pick is not valid!";
        }

        return null;
    }

    @Override
    public DraftChoice getNothingPickedChoice() {
        return new DraftChoice(
                getType(),
                null,
                null,
                "No seat picked",
                MiltyDraftEmojis.getSpeakerPickEmoji(-1).toString());
    }

    @Override
    public String save() {
        return "" + numSeats;
    }

    @Override
    public void load(String data) {
        numSeats = Integer.parseInt(data);
    }

    @Override
    public String validateState(DraftManager draftManager) {
        if (numSeats < 1) {
            return "Number of seats must be at least 1, but is: " + numSeats;
        }
        int numPlayers = draftManager.getPlayerStates().size();
        if (numSeats < numPlayers) {
            return "Number of seats (" + numSeats + ") is less than number of players (" + numPlayers + ")";
        }

        // Ensure seat count is consistent with map template
        MapTemplateModel mapTemplate =
                Mapper.getMapTemplate(draftManager.getGame().getMapTemplateID());
        if (mapTemplate == null) {
            return "Map template ID is not set or invalid: "
                    + draftManager.getGame().getMapTemplateID();
        }
        if (mapTemplate.getPlayerCount() < numSeats) {
            return "Map template " + mapTemplate.getAlias() + " only supports " + mapTemplate.getPlayerCount()
                    + " players, but draft has " + numSeats + " seats.";
        }

        // Ensure no two players have picked the same seat.
        List<DraftChoice> seatChoices = draftManager.getAllPicksOfType(getType());
        Set<String> chosenSeats = new HashSet<>();
        for (DraftChoice choice : seatChoices) {
            if (chosenSeats.contains(choice.getChoiceKey())) {
                return "Multiple players have chosen seat " + choice.getChoiceKey();
            }
            chosenSeats.add(choice.getChoiceKey());
        }

        return null;
    }

    @Override
    public Consumer<Player> setupPlayer(
            DraftManager draftManager, String playerUserId, PlayerSetupState playerSetupState) {
        PlayerDraftState pState = draftManager.getPlayerStates().get(playerUserId);
        if (!pState.getPicks().containsKey(getType())
                || pState.getPicks().get(getType()).isEmpty()) {
            throw new IllegalStateException("Player " + playerUserId + " has not picked a seat");
        }

        String seat = pState.getPicks().get(getType()).get(0).getChoiceKey();
        int seatNum = Integer.parseInt(seat);

        String homeTilePosition = MapTemplateHelper.getPlayerHomeSystemLocation(
                seatNum, draftManager.getGame().getMapTemplateID());
        playerSetupState.setPositionHS(homeTilePosition);

        return null;
    }
}
