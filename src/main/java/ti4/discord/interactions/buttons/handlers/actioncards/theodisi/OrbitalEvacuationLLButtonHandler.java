package ti4.discord.interactions.buttons.handlers.actioncards.theodisi;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Helper;
import ti4.helpers.NewStuffHelper;
import ti4.helpers.Units.UnitType;
import ti4.message.MessageHelper;
import ti4.service.unit.AddUnitService;
import ti4.service.unit.RemoveUnitService;

@UtilityClass
public class OrbitalEvacuationLLButtonHandler {
    private static final String RESOLVE = "resolveOrbitalEvacuation";
    private static final String SOURCE = "orbitalEvacuationSource_";
    private static final String DESTINATION = "orbitalEvacuationDestination_";
    private static final String STATE = "orbitalEvacuation_";

    @ButtonHandler(RESOLVE)
    public static void resolveOrbitalEvacuation(ButtonInteractionEvent event, Game game, Player player) {
        ButtonHelper.deleteMessage(event);
        sendSourceButtons(game, player, event.getMessageChannel());
    }

    @ButtonHandler(SOURCE)
    public static void selectSource(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        List<Button> sourceButtons = getSourceButtons(game, player);
        List<Button> extraButtons =
                List.of(Buttons.red(player.factionButtonChecker() + "deleteButtons", "Done Evacuating"));
        String sourceMessage = player.getRepresentationNoPing()
                + ", move each of your ground forces from controlled planets in the active system with _Orbital Evacuation_.";
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event,
                event.getMessageChannel(),
                sourceButtons,
                extraButtons,
                sourceMessage,
                player.factionButtonChecker() + SOURCE,
                buttonID)) {
            return;
        }
        String[] parts = buttonID.substring(SOURCE.length()).split("\\|", 2);
        UnitType type;
        try {
            type = parts.length == 2 ? UnitType.valueOf(parts[0]) : null;
        } catch (IllegalArgumentException e) {
            type = null;
        }
        String source = parts.length == 2 ? parts[1] : "";
        Tile sourceTile = game.getTileFromPlanet(source);
        Planet sourcePlanet = game.getUnitHolderFromPlanet(source);
        if (type == null || sourceTile == null || sourcePlanet == null || sourcePlanet.getUnitCount(type, player) < 1)
            return;
        game.setStoredValue(STATE + player.getFaction(), type.name() + "|" + source);
        List<Button> buttons = getDestinationButtons(game, player, source);
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), "You control no other planet for _Orbital Evacuation_.");
            return;
        }
        MessageHelper.editMessageWithButtons(
                event,
                player.getRepresentationNoPing() + ", choose where to move this " + type.humanReadableName()
                        + " for _Orbital Evacuation_.",
                NewStuffHelper.buttonPagination(buttons, player.factionButtonChecker() + DESTINATION, 0));
    }

    @ButtonHandler(DESTINATION)
    public static void moveGroundForce(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] state = game.getStoredValue(STATE + player.getFaction()).split("\\|", 2);
        String source = state.length == 2 ? state[1] : "";
        List<Button> buttons = getDestinationButtons(game, player, source);
        String message = player.getRepresentationNoPing() + ", choose where to move this "
                + (state.length == 2 ? state[0].toLowerCase() : "ground force") + " for _Orbital Evacuation_.";
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event,
                event.getMessageChannel(),
                buttons,
                message,
                player.factionButtonChecker() + DESTINATION,
                buttonID)) {
            return;
        }
        String destination = buttonID.substring(DESTINATION.length());
        UnitType type;
        try {
            type = state.length == 2 ? UnitType.valueOf(state[0]) : null;
        } catch (IllegalArgumentException e) {
            type = null;
        }
        Tile sourceTile = state.length == 2 ? game.getTileFromPlanet(state[1]) : null;
        Planet sourcePlanet = state.length == 2 ? game.getUnitHolderFromPlanet(state[1]) : null;
        Tile destinationTile = game.getTileFromPlanet(destination);
        if (type == null
                || sourceTile == null
                || sourcePlanet == null
                || destinationTile == null
                || !player.hasPlanet(destination)
                || destination.equals(state[1])
                || sourcePlanet.getUnitCount(type, player) < 1) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "That movement is no longer legal.");
            return;
        }
        RemoveUnitService.removeUnit(event, sourceTile, game, player, sourcePlanet, type, 1);
        AddUnitService.addUnits(event, destinationTile, game, player.getColor(), type.value + " " + destination);
        game.removeStoredValue(STATE + player.getFaction());
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + " moved 1 "
                        + type.humanReadableName() + " from " + Helper.getPlanetRepresentation(state[1], game) + " to "
                        + Helper.getPlanetRepresentation(destination, game) + " with _Orbital Evacuation_.");
        ButtonHelper.deleteMessage(event);
        sendSourceButtons(game, player, event.getMessageChannel());
    }

    private static void sendSourceButtons(
            Game game, Player player, net.dv8tion.jda.api.entities.channel.middleman.MessageChannel channel) {
        List<Button> buttons = getSourceButtons(game, player);
        if (buttons.isEmpty()) return;
        MessageHelper.sendMessageToChannelWithButtons(
                channel,
                player.getRepresentationNoPing()
                        + ", move each of your ground forces from controlled planets in the active system with _Orbital Evacuation_.",
                NewStuffHelper.buttonPagination(
                        buttons,
                        List.of(Buttons.red(player.factionButtonChecker() + "deleteButtons", "Done Evacuating")),
                        player.factionButtonChecker() + SOURCE,
                        25,
                        0,
                        false));
    }

    private static List<Button> getSourceButtons(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        Tile active = game.getTileByPosition(game.getActiveSystem());
        if (active == null) return buttons;
        for (Planet planet : active.getPlanetUnitHolders()) {
            if (!player.hasPlanet(planet.getName())) continue;
            for (UnitType type : List.of(UnitType.Infantry, UnitType.Mech)) {
                if (planet.getUnitCount(type, player) > 0) {
                    buttons.add(Buttons.green(
                            player.factionButtonChecker() + SOURCE + type.name() + "|" + planet.getName(),
                            "Move 1 " + type.humanReadableName() + " from "
                                    + Helper.getPlanetRepresentation(planet.getName(), game)));
                }
            }
        }
        return buttons;
    }

    private static List<Button> getDestinationButtons(Game game, Player player, String source) {
        List<Button> buttons = new ArrayList<>();
        for (String planet : player.getPlanets()) {
            if (!planet.equals(source) && game.getTileFromPlanet(planet) != null) {
                buttons.add(Buttons.green(
                        player.factionButtonChecker() + DESTINATION + planet,
                        "Move to " + Helper.getPlanetRepresentation(planet, game)));
            }
        }
        return buttons;
    }
}
