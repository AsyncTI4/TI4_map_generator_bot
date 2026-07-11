package ti4.discord.interactions.buttons.handlers.actioncards.acd2;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.buttons.ButtonStyle;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Helper;
import ti4.helpers.Units.UnitType;
import ti4.message.MessageHelper;
import ti4.service.planet.PlanetButtonService;
import ti4.service.unit.AddUnitService;
import ti4.service.unit.RemoveUnitService;

@UtilityClass
class MassTransferenceAcd2ButtonHandler {

    private static final UnitType[] PLANET_UNITS = {UnitType.Infantry, UnitType.Mech, UnitType.Pds, UnitType.Spacedock};

    private static String stateKey(Player player) {
        return "massTransfer" + player.getFaction();
    }

    @ButtonHandler("resolveMassTransference")
    public static void resolveMassTransference(Player player, Game game, ButtonInteractionEvent event) {
        ButtonHelper.deleteMessage(event);
        Tile activeTile = getActiveTile(game);
        if (activeTile == null) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationUnfogged()
                            + ", there is no active system to resolve _Mass Transference_ from.");
            return;
        }
        sendSourceButtons(player, game, 4);
    }

    @ButtonHandler("massTransSource_")
    public static void resolveMassTransSource(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String[] parts = buttonID.replace("massTransSource_", "").split("_", 3);
        ButtonHelper.deleteMessage(event);
        if (parts.length < 3) {
            return;
        }
        game.setStoredValue(stateKey(player), parts[0] + "_" + parts[1] + "_" + parts[2]);

        List<Button> buttons = new ArrayList<>(PlanetButtonService.buttonsForOwnedPlanets(
                player, game, location -> true, ButtonStyle.SUCCESS, player.factionButtonChecker() + "massTransDest_"));
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationUnfogged() + ", you control no planets to place the unit on.");
            return;
        }
        buttons.add(Buttons.red("deleteButtons", "Cancel"));
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", choose which planet you control to place the "
                        + unitTypeFromName(parts[1]).humanReadableName() + " on for _Mass Transference_.",
                buttons);
    }

    @ButtonHandler("massTransDest_")
    public static void resolveMassTransDest(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String destPlanet = buttonID.replace("massTransDest_", "");
        ButtonHelper.deleteMessage(event);

        String[] state = game.getStoredValue(stateKey(player)).split("_", 3);
        if (state.length < 3) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "Could not resolve _Mass Transference_.");
            return;
        }
        int remaining;
        try {
            remaining = Integer.parseInt(state[0]);
        } catch (NumberFormatException e) {
            return;
        }
        UnitType type = unitTypeFromName(state[1]);
        String sourcePlanet = state[2];
        Tile sourceTile = game.getTileContainingPlanet(sourcePlanet);
        Planet sourceUH = game.getPlanet(sourcePlanet);
        Tile destTile = game.getTileContainingPlanet(destPlanet);
        if (type == null || sourceTile == null || sourceUH == null || destTile == null) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "Could not resolve _Mass Transference_.");
            return;
        }

        RemoveUnitService.removeUnit(event, sourceTile, game, player, sourceUH, type, 1);
        AddUnitService.addUnits(event, destTile, game, player.getColor(), type.value + " " + destPlanet);
        game.removeStoredValue(stateKey(player));
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + " moved 1 " + type.humanReadableName() + " from "
                        + Helper.getPlanetRepresentation(sourcePlanet, game) + " to "
                        + Helper.getPlanetRepresentation(destPlanet, game) + " for _Mass Transference_.");

        if (remaining > 1) {
            sendSourceButtons(player, game, remaining - 1);
        }
    }

    private static void sendSourceButtons(Player player, Game game, int remaining) {
        Tile activeTile = getActiveTile(game);
        if (activeTile == null) {
            return;
        }
        List<Button> buttons = new ArrayList<>();
        for (Planet planet : activeTile.getPlanetUnitHolders()) {
            if (!player.hasPlanet(planet.getName())) {
                continue;
            }
            for (UnitType type : PLANET_UNITS) {
                int count = planet.getUnitCount(type, player);
                if (count > 0) {
                    buttons.add(Buttons.green(
                            player.factionButtonChecker() + "massTransSource_" + remaining + "_" + type.name() + "_"
                                    + planet.getName(),
                            "Move 1 " + type.humanReadableName() + " from "
                                    + Helper.getPlanetRepresentation(planet.getName(), game) + " (" + count
                                    + " there)"));
                }
            }
        }
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationUnfogged()
                            + ", you have no units left on planets in the active system for _Mass Transference_.");
            return;
        }
        buttons.add(Buttons.red("deleteButtons", "Done"));
        String remainingText = remaining == 1 ? "1 unit remaining" : remaining + " units remaining";
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", choose a unit to relocate for _Mass Transference_ ("
                        + remainingText + ").",
                buttons);
    }

    private static Tile getActiveTile(Game game) {
        String pos = game.getActiveSystem();
        if (pos == null || pos.isEmpty()) {
            return null;
        }
        return game.getTileByPosition(pos);
    }

    private static UnitType unitTypeFromName(String name) {
        try {
            return UnitType.valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
