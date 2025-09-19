package ti4.service.draft.draftables;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import lombok.Getter;
import lombok.Setter;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.helpers.MapTemplateHelper;
import ti4.image.Mapper;
import ti4.map.Player;
import ti4.model.MapTemplateModel;
import ti4.service.draft.DraftChoice;
import ti4.service.draft.DraftManager;
import ti4.service.draft.DraftableType;
import ti4.service.draft.PlayerDraftState;
import ti4.service.draft.PlayerSetupService.PlayerSetupState;
import ti4.service.emoji.MiltyDraftEmojis;

public class SeatDraftable extends SinglePickDraftable {

    @Getter
    @Setter
    private int numSeats;

    public void initialize(int numSeats) {
        this.numSeats = numSeats;
    }

    public static Integer getSeatNumberFromChoiceKey(String choiceKey) {
        if (choiceKey == null) return null;
        if (!choiceKey.startsWith("seat")) return null;
        return Integer.parseInt(choiceKey.substring(4));
    }

    public static final DraftableType TYPE = DraftableType.of("Seat");

    @Override
    public DraftableType getType() {
        return TYPE;
    }

    @Override
    public List<DraftChoice> getAllDraftChoices() {
        List<DraftChoice> choices = new ArrayList<>();
        for (int i = 1; i <= numSeats; i++) {
            String choiceKey = "seat" + i;
            String buttonEmoji = MiltyDraftEmojis.getSpeakerPickEmoji(i).toString();
            String unformattedName = "Seat " + i;
            String displayName = "Seat " + i;
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
    public String handleCustomCommand(
            GenericInteractionCreateEvent event, DraftManager draftManager, String playerUserId, String commandKey) {

        return "Unknown command: " + commandKey;
    }

    @Override
    public DraftChoice getNothingPickedChoice() {
        return new DraftChoice(
                getType(),
                null,
                null,
                "No seat picked",
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

        return super.validateState(draftManager);
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
        Integer seatNum = getSeatNumberFromChoiceKey(seat);
        if (seatNum == null) {
            throw new IllegalStateException("Player " + playerUserId + " has an invalid seat choice key: " + seat);
        }

        String homeTilePosition = MapTemplateHelper.getPlayerHomeSystemLocation(
                seatNum, draftManager.getGame().getMapTemplateID());
        playerSetupState.setPositionHS(homeTilePosition);

        return null;
    }
}
