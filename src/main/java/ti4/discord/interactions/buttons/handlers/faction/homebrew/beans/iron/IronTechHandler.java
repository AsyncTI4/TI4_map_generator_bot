package ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.iron;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Helper;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;
import ti4.service.unit.AddUnitService;
import ti4.service.unit.RemoveUnitService;

@UtilityClass
public class IronTechHandler {
    private static final String SELECT_MODULAR_FLEET_SYSTEM = "ironSelectModularFleetSystem_";
    private static final String SELECT_MODULAR_FLEET_SHIP = "ironSelectModularFleetShip_";
    private static final String SELECT_MODULAR_FLEET_REPLACEMENT = "ironSelectModularFleetReplacement_";
    private static final String PAY_MODULAR_FLEET = "ironPayModularFleet_";

    @ButtonHandler("ironUseModularFleet")
    public static void offerModularFleetShips(ButtonInteractionEvent event, Game game, Player player) {
        List<Button> buttons = new ArrayList<>();

        for (Tile tile : game.getTileMap().values()) {
            if (Helper.getProductionValue(player, game, tile, false) < 1 || !hasModularFleetShip(player, tile))
                continue;
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + SELECT_MODULAR_FLEET_SYSTEM + tile.getPosition(),
                    tile.getRepresentationForButtons(game, player),
                    tile.getTileEmoji(player)));
        }

        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationNoPing() + ", you have no eligible ships for _Modular Fleet_.");
            return;
        }

        buttons.add(Buttons.DECLINE_DELETE_BUTTONS);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + ", choose a system for _Modular Fleet_.",
                buttons);
        ButtonHelper.deleteTheOneButton(event);
    }

    @ButtonHandler(SELECT_MODULAR_FLEET_SYSTEM)
    public static void offerModularFleetShipsInSystem(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        Tile tile = game.getTileByPosition(buttonID.substring(SELECT_MODULAR_FLEET_SYSTEM.length()));
        if (tile == null) return;
        List<Button> buttons = tile.getSpaceUnitHolder().getUnitKeysForPlayer(player).stream()
                .map(player::getUnitFromUnitKey)
                .filter(unit -> unit != null && unit.isNonFighterShip())
                .map(unit -> Buttons.green(
                        player.factionButtonChecker() + SELECT_MODULAR_FLEET_SHIP + tile.getPosition() + "|"
                                + unit.getAsyncId(),
                        "Replace " + unit.getName(),
                        unit.getUnitEmoji()))
                .toList();
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + ", choose a ship to replace in "
                        + tile.getRepresentationForButtons(game, player) + ".",
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(SELECT_MODULAR_FLEET_SHIP)
    public static void offerModularFleetReplacements(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload =
                buttonID.substring(SELECT_MODULAR_FLEET_SHIP.length()).split("\\|", 2);
        if (payload.length != 2) {
            return;
        }

        Tile tile = game.getTileByPosition(payload[0]);
        UnitModel sourceShip = player.getUnitFromAsyncID(payload[1]);
        if (tile == null || sourceShip == null) {
            return;
        }

        List<Button> buttons = new ArrayList<>(player.getUnitModels().stream()
                .filter(UnitModel::isNonFighterShip)
                .filter(unit -> unit.getCost() > sourceShip.getCost())
                .sorted(java.util.Comparator.comparing(UnitModel::getCost))
                .map(unit -> Buttons.green(
                        player.factionButtonChecker()
                                + SELECT_MODULAR_FLEET_REPLACEMENT
                                + tile.getPosition()
                                + "|"
                                + sourceShip.getAsyncId()
                                + "|"
                                + unit.getAsyncId(),
                        "Replace with " + unit.getName() + " (" + unit.getCost() + ")",
                        unit.getUnitEmoji()))
                .toList());

        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentationNoPing()
                            + ", you have no higher-cost non-fighter ship available for _Modular Fleet_.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        buttons.add(Buttons.DECLINE_DELETE_BUTTONS);
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + ", choose the replacement ship. You will spend the cost difference.",
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(SELECT_MODULAR_FLEET_REPLACEMENT)
    public static void payForModularFleetReplacement(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload =
                buttonID.substring(SELECT_MODULAR_FLEET_REPLACEMENT.length()).split("\\|", 3);
        if (payload.length != 3) {
            return;
        }

        Tile tile = game.getTileByPosition(payload[0]);
        UnitModel sourceShip = player.getUnitFromAsyncID(payload[1]);
        UnitModel replacementShip = player.getUnitFromAsyncID(payload[2]);
        if (tile == null || sourceShip == null || replacementShip == null) {
            return;
        }

        float costDifference = replacementShip.getCost() - sourceShip.getCost();
        List<Button> paymentButtons = new ArrayList<>(ButtonHelper.getExhaustButtonsWithTG(game, player, "res"));

        paymentButtons.add(Buttons.red(
                player.factionButtonChecker()
                        + PAY_MODULAR_FLEET
                        + tile.getPosition()
                        + "|"
                        + sourceShip.getAsyncId()
                        + "|"
                        + replacementShip.getAsyncId(),
                "Done Paying"));

        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentationNoPing()
                        + ", spend "
                        + costDifference
                        + " resource"
                        + (costDifference == 1 ? "" : "s")
                        + " to replace your "
                        + sourceShip.getName()
                        + " with a "
                        + replacementShip.getName()
                        + ".",
                paymentButtons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(PAY_MODULAR_FLEET)
    public static void resolveModularFleetReplacement(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload = buttonID.substring(PAY_MODULAR_FLEET.length()).split("\\|", 3);
        if (payload.length != 3) {
            return;
        }

        Tile tile = game.getTileByPosition(payload[0]);
        UnitModel sourceShip = player.getUnitFromAsyncID(payload[1]);
        UnitModel replacementShip = player.getUnitFromAsyncID(payload[2]);
        if (tile == null || sourceShip == null || replacementShip == null) {
            return;
        }

        RemoveUnitService.removeUnit(event, tile, game, player, tile.getSpaceUnitHolder(), sourceShip.getUnitType(), 1);
        AddUnitService.addUnits(event, tile, game, player.getColor(), "1 " + replacementShip.getAsyncId());

        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing()
                        + " replaced 1 "
                        + sourceShip.getName()
                        + " with 1 "
                        + replacementShip.getName()
                        + " using _Modular Fleet_.");
        ButtonHelper.deleteMessage(event);
    }

    private static boolean hasModularFleetShip(Player player, Tile tile) {
        return tile.getSpaceUnitHolder().getUnitKeysForPlayer(player).stream()
                .map(player::getUnitFromUnitKey)
                .anyMatch(unit -> unit != null && unit.isNonFighterShip());
    }
}
