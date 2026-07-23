package ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.Thrones;

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
import ti4.helpers.FoWHelper;
import ti4.helpers.Units.UnitKey;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;
import ti4.service.emoji.FactionEmojis;
import ti4.service.unit.AddUnitService;
import ti4.service.unit.RemoveUnitService;

@UtilityClass
public class ThronesTechHandler {
    private static final String SS = "ththronesb";
    private static final String USE_SS = "useSpecterStep";
    private static final String SELECT_SS_SHIP = "selectShipForSS_";
    private static final String MOVE_SS_SHIP = "moveSpecterStepShip_";

    public static Button getSpecterStepButton(Player player) {
        return Buttons.green(player.factionButtonChecker() + USE_SS, "Use Specter Step", FactionEmojis.thrones);
    }

    @ButtonHandler(USE_SS)
    public static void startSpecterStep(ButtonInteractionEvent event, Game game, Player player) {
        if (game == null || player == null || !player.hasTechReady(SS)) {
            return;
        }

        Tile activeTile = game.getTileByPosition(game.getActiveSystem());
        List<Button> buttons = new ArrayList<>();

        if (activeTile != null) {
            for (UnitKey unitKey : activeTile
                    .getSpaceUnitHolder()
                    .getUnitsByStateForPlayer(player)
                    .keySet()) {
                UnitModel unitModel = player.getUnitFromUnitKey(unitKey);
                if (unitModel == null || !unitModel.getIsShip()) {
                    continue;
                }

                buttons.add(Buttons.green(
                        player.factionButtonChecker() + SELECT_SS_SHIP + unitKey.asyncID(),
                        "Move 1 " + unitModel.getName(),
                        unitKey.unitEmoji()));
            }
        }

        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentation() + " has no ships in the active system to move with _Specter Step_.");
            return;
        }

        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentation() + ", please choose which ship to move using _Specter Step_:",
                buttons);
    }

    @ButtonHandler(SELECT_SS_SHIP)
    public static void selectSpecterAdjacentSystem(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (game == null || player == null || !player.hasTechReady(SS)) {
            return;
        }

        String asyncId = buttonID.substring(SELECT_SS_SHIP.length());
        UnitModel selectedShip = player.getUnitFromAsyncID(asyncId);
        if (selectedShip == null || !selectedShip.getIsShip()) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        Tile activeTile = game.getTileByPosition(game.getActiveSystem());
        if (activeTile == null
                || activeTile.getSpaceUnitHolder().getUnitsByStateForPlayer(player).keySet().stream()
                        .noneMatch(unitKey -> asyncId.equals(unitKey.asyncID()))) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        List<Button> adjacentSystems = new ArrayList<>();
        for (String position :
                FoWHelper.getAdjacentTilesAndNotThisTile(game, activeTile.getPosition(), player, false)) {
            Tile tile = game.getTileByPosition(position);
            if (tile == null
                    || !FoWHelper.playerHasUnitsInSystem(player, tile)
                    || game.getRealPlayersNDummies().stream()
                            .anyMatch(otherPlayer -> otherPlayer != player
                                    && FoWHelper.playerHasActualShipsInSystem(otherPlayer, tile))) {
                continue;
            }

            adjacentSystems.add(Buttons.green(
                    player.factionButtonChecker() + MOVE_SS_SHIP + asyncId + "|" + tile.getPosition(),
                    tile.getRepresentationForButtons(game, player)));
        }

        if (adjacentSystems.isEmpty()) {
            ButtonHelper.deleteMessage(event);
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentation() + " has no legal destination for _Specter Step_.");
            return;
        }

        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentation() + ", select the system to move the ship to:",
                adjacentSystems);
    }

    @ButtonHandler(MOVE_SS_SHIP)
    public static void resolveSpecterStepMovement(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (game == null || player == null || !player.hasTechReady(SS)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        String payload = buttonID.substring(MOVE_SS_SHIP.length());
        String[] parts = payload.split("\\|", 2);
        if (parts.length != 2) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        String asyncId = parts[0];
        Tile source = game.getTileByPosition(game.getActiveSystem());
        Tile destination = game.getTileByPosition(parts[1]);
        if (source == null
                || destination == null
                || !FoWHelper.getAdjacentTilesAndNotThisTile(game, source.getPosition(), player, false)
                        .contains(destination.getPosition())
                || !FoWHelper.playerHasUnitsInSystem(player, destination)
                || game.getRealPlayersNDummies().stream()
                        .anyMatch(otherPlayer -> otherPlayer != player
                                && FoWHelper.playerHasActualShipsInSystem(otherPlayer, destination))) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        UnitKey shipKey = source.getSpaceUnitHolder().getUnitsByStateForPlayer(player).keySet().stream()
                .filter(unitKey -> asyncId.equals(unitKey.asyncID()))
                .findFirst()
                .orElse(null);
        UnitModel ship = shipKey == null ? null : player.getUnitFromUnitKey(shipKey);
        if (ship == null || !ship.getIsShip()) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        var removedShips = RemoveUnitService.removeUnits(event, source, game, player.getColor(), "1 " + asyncId, false);
        if (removedShips.isEmpty()) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        AddUnitService.addUnits(event, destination, game, player.getColor(), "1 " + asyncId, removedShips);
        player.exhaustTech(SS);
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentation() + " moved 1 " + ship.getName() + " from "
                        + source.getRepresentationForButtons(game, player) + " to "
                        + destination.getRepresentationForButtons(game, player) + " with _Specter Step_.");
    }
}
