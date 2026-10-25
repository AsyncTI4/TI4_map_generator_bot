package ti4.discord.interactions.buttons.handlers.actioncards.bluereverie;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.helpers.FoWHelper;
import ti4.helpers.Units.UnitType;
import ti4.message.MessageHelper;
import ti4.service.unit.AddUnitService;

@UtilityClass
public class StasisTeamsBrActionCardHandler {
    private static final String RESOLVE = "resolveStasisTeamsBr";
    private static final String SELECT_PLAYER = "stasisTeamsPlayer_";
    private static final String CAPTURE = "stasisTeamsCapture_";
    private static final String DONE = "stasisTeamsDone";
    private static final List<UnitType> CAPTURABLE_UNITS = List.of(
            UnitType.Infantry,
            UnitType.Mech,
            UnitType.Pds,
            UnitType.Spacedock,
            UnitType.Fighter,
            UnitType.Destroyer,
            UnitType.Cruiser,
            UnitType.Carrier,
            UnitType.Dreadnought,
            UnitType.Flagship,
            UnitType.Warsun);

    public static void offerResolution(Game game, Player player, String message) {
        MessageHelper.sendMessageToChannelWithButton(
                player.getCorrectChannel(),
                message,
                Buttons.green(player.factionButtonChecker() + RESOLVE, "Resolve Stasis Teams"));
    }

    @ButtonHandler(RESOLVE)
    public static void resolve(ButtonInteractionEvent event, Game game, Player player) {
        List<Button> buttons = game.getRealPlayersNNeutral().stream()
                .filter(target -> target != player)
                .map(target -> FoWHelper.fogSafeTargetButton(
                        player.factionButtonChecker() + SELECT_PLAYER + target.getFaction(), "gray", target, player))
                .toList();
        if (buttons.isEmpty()) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        MessageHelper.editMessageWithButtons(
                event,
                player.getRepresentationNoPing() + ", choose the player whose destroyed units you are capturing.",
                buttons);
    }

    @ButtonHandler(SELECT_PLAYER)
    public static void selectPlayer(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        Player destroyedUnitOwner = game.getPlayerFromColorOrFaction(buttonID.substring(SELECT_PLAYER.length()));
        if (destroyedUnitOwner == null || destroyedUnitOwner == player) {
            ButtonHelper.deleteTheOneButton(event);
            return;
        }
        MessageHelper.editMessageWithButtons(
                event,
                player.getRepresentationNoPing() + ", select each destroyed unit belonging to "
                        + FoWHelper.identityOrColorIfCanSeeStats(
                                game, destroyedUnitOwner, player, destroyedUnitOwner.getRepresentationNoPing())
                        + ".",
                getCaptureButtons(player, destroyedUnitOwner));
    }

    @ButtonHandler(CAPTURE)
    public static void capture(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] parts = buttonID.substring(CAPTURE.length()).split("\\|", 2);
        Player destroyedUnitOwner = parts.length == 2 ? game.getPlayerFromColorOrFaction(parts[0]) : null;
        UnitType type = getUnitType(parts.length == 2 ? parts[1] : "");
        if (destroyedUnitOwner == null || destroyedUnitOwner == player || type == null) {
            ButtonHelper.deleteTheOneButton(event);
            return;
        }
        AddUnitService.addUnits(
                event, player.getNomboxTile(), game, destroyedUnitOwner.getColor(), "1 " + type.getValue());
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + " captured 1 " + destroyedUnitOwner.getColor() + " "
                        + type.humanReadableName() + " with _Stasis Teams_.");
    }

    @ButtonHandler(DONE)
    public static void done(ButtonInteractionEvent event) {
        ButtonHelper.deleteMessage(event);
    }

    private static List<Button> getCaptureButtons(Player player, Player destroyedUnitOwner) {
        List<Button> buttons = new ArrayList<>();
        for (UnitType type : CAPTURABLE_UNITS) {
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + CAPTURE + destroyedUnitOwner.getFaction() + "|" + type.name(),
                    "Capture " + type.humanReadableName()));
        }
        buttons.add(Buttons.red(player.factionButtonChecker() + DONE, "Done Capturing"));
        return buttons;
    }

    private static UnitType getUnitType(String value) {
        try {
            UnitType type = UnitType.valueOf(value);
            return CAPTURABLE_UNITS.contains(type) ? type : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
