package ti4.service.draft.draftables;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import lombok.Getter;
import lombok.Setter;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.utils.FileUpload;
import ti4.helpers.MapTemplateHelper;
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
import ti4.service.emoji.MiscEmojis;

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
            String buttonEmoji = MiscEmojis.getResourceEmoji(i);
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
                getType(), null, null, "No seat picked", "No seat picked", MiscEmojis.resources.toString());
    }

    @Override
    public FileUpload generateSummaryImage(
            DraftManager draftManager, String uniqueKey, List<String> restrictChoiceKeys) {

        if (draftManager.getGame().getMapTemplateID() != null) {
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
                    + draftManager.getGame().getMapTemplateID() + ". Set it with `/game set_map_template`.";
        }
        if (mapTemplate.getPlayerCount() < numSeats) {
            return "Map template " + mapTemplate.getAlias() + " only supports " + mapTemplate.getPlayerCount()
                    + " players, but draft has " + numSeats
                    + " seats. Change the map template with `/game set_map_template` or reduce the number of seats with `/draft seat set_num_seats`.";
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

        // Seat count comes from map template on the draft settings, falling back to map template on the game, then just
        // use player count.

        MapTemplateModel mapTemplate =
                draftSystemSettings.getSliceSettings().getMapTemplate().getValue();
        if (mapTemplate != null) {
            initialize(mapTemplate.getPlayerCount());
            return null;
        }

        mapTemplate = game.getMapTemplateID() != null ? Mapper.getMapTemplate(game.getMapTemplateID()) : null;
        if (mapTemplate != null) {
            initialize(mapTemplate.getPlayerCount());
            return null;
        }

        initialize(draftSystemSettings.getPlayerUserIds().size());
        return null;
    }
}
