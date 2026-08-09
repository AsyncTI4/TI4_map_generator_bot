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
import ti4.helpers.Constants;
import ti4.helpers.FoWHelper;
import ti4.helpers.NewStuffHelper;
import ti4.helpers.Units.UnitKey;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;
import ti4.service.emoji.FactionEmojis;
import ti4.service.unit.AddUnitService;
import ti4.service.unit.RemoveUnitService;

@UtilityClass
public class ThronesTechHandler {
    // Specter Step
    private static final String SS = "ththronesb";
    private static final String USE_SS = "useSpecterStep";
    private static final String SELECT_SS_SHIP = "selectShipForSS_";
    private static final String MOVE_SS_SHIP = "moveSpecterStepShip_";
    // Rift-Touched Bastion
    private static final String RTB = "ththronesr";
    private static final String USE_RTB = "useRiftTouchedBastion";
    private static final String SELECT_RTB_SYSTEM = "selectRiftTouchedBastionSystem_";
    private static final String RTB_RIFT = "riftTouchedBastionRift_";

    // Specter Step
    public static List<Button> getSpecterStepButtons(Player player) {
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.green(player.factionButtonChecker() + USE_SS, "Use Specter Step", FactionEmojis.thrones));
        buttons.add(Buttons.red("deleteButtons", "Decline"));
        return buttons;
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
                player.getRepresentation() + ", please choose the ship to move using _Specter Step_.",
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
                player.getRepresentation() + ", please choose the system to which to move the ship.",
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

    // Rift-Touched Bastion
    public static void offerRiftTouchedBastion(Game game, Tile activatedTile) {
        for (Player player : game.getRealPlayers()) {
            if (!player.hasTechReady(RTB) || !FoWHelper.playerHasUnitsInSystem(player, activatedTile)) {
                continue;
            }

            MessageHelper.sendMessageToChannelWithButtons(
                    player.getCorrectChannel(),
                    player.getRepresentation()
                            + ", you may exhaust _Rift-Touched Bastion_ to treat a system containing your units as a gravity rift for this tactical action.",
                    List.of(
                            Buttons.green(
                                    player.factionButtonChecker() + USE_RTB,
                                    "Use Rift-Touched Bastion",
                                    FactionEmojis.thrones),
                            Buttons.red("deleteButtons", "Decline")));
        }
    }

    @ButtonHandler(USE_RTB)
    public static void offerRiftTouchedBastionSystems(ButtonInteractionEvent event, Game game, Player player) {
        if (!player.hasTechReady(RTB)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        List<Button> buttons = getRiftTouchedBastionSystemButtons(game, player);
        if (buttons.isEmpty()) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        String message = player.getRepresentation()
                + ", choose a system containing your units to treat as a gravity rift for this tactical action.";

        String prefix = player.factionButtonChecker() + SELECT_RTB_SYSTEM;
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(), message, NewStuffHelper.buttonPagination(buttons, prefix, 0));
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
    }

    @ButtonHandler(SELECT_RTB_SYSTEM)
    public static void resolveRiftTouchedBastion(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (!player.hasTechReady(RTB)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        List<Button> buttons = getRiftTouchedBastionSystemButtons(game, player);
        String message = player.getRepresentationNoPing()
                + ", choose a system containing your units to treat as a gravity rift for this tactical action.";
        String prefix = player.factionButtonChecker() + SELECT_RTB_SYSTEM;

        if (NewStuffHelper.checkAndHandlePaginationChange(
                event, event.getMessageChannel(), buttons, message, prefix, buttonID)) {
            return;
        }

        String position = buttonID.substring(SELECT_RTB_SYSTEM.length());
        Tile tile = game.getTileByPosition(position);
        if (tile == null || !FoWHelper.playerHasUnitsInSystem(player, tile)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        player.exhaustTech(RTB);

        if (!tile.isGravityRift()) {
            tile.addToken("token_gravityrift.png", Constants.SPACE);
            game.setStoredValue(RTB_RIFT + player.getFaction(), tile.getPosition());
        }
        game.setStoredValue("possiblyUsedRift", "yes");

        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentation() + " exhausted _Rift-Touched Bastion_. "
                        + tile.getRepresentationForButtons(game, player)
                        + " is treated as a gravity rift for this tactical action.");

        ButtonHelper.deleteMessage(event);
    }

    private static List<Button> getRiftTouchedBastionSystemButtons(Game game, Player player) {
        return game.getTileMap().values().stream()
                .filter(tile -> FoWHelper.playerHasUnitsInSystem(player, tile))
                .map(tile -> Buttons.green(
                        player.factionButtonChecker() + SELECT_RTB_SYSTEM + tile.getPosition(),
                        tile.getRepresentationForButtons(game, player),
                        FactionEmojis.thrones))
                .toList();
    }

    public static void clearRiftTouchedBastion(Game game) {
        game.getStoredValueMap().keySet().stream()
                .filter(key -> key.startsWith(RTB_RIFT))
                .toList()
                .forEach(key -> {
                    Tile tile = game.getTileByPosition(game.getStoredValue(key));
                    if (tile != null) {
                        tile.removeToken("token_gravityrift.png", Constants.SPACE);
                    }
                    game.removeStoredValue(key);
                });
    }
}
