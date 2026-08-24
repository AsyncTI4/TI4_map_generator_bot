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
import ti4.helpers.Units.UnitType;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;
import ti4.service.emoji.FactionEmojis;
import ti4.service.unit.AddUnitService;
import ti4.service.unit.MoveUnitService;
import ti4.service.unit.RemoveUnitService;

@UtilityClass
public class ThronesTechHandler {
    // Specter Step
    private static final String SS = "ththronesb";
    private static final String USE_SS = "useSpecterStep";
    private static final String SELECT_SS_SHIP = "selectShipForSS_";
    private static final String MOVE_SS_SHIP = "moveSpecterStepShip_";
    private static final String MOVE_SS_TRANSPORT = "moveSpecterStepTransport_";
    private static final String DONE_SS_TRANSPORT = "doneSpecterStepTransport_";
    private static final String SS_TRANSPORT_PAGE = "specterStepTransportPage_";
    private static final String SS_DESTINATION_PAGE = "specterStepDestinationPage_";
    // Rift-Touched Bastion
    private static final String RTB = "ththronesr";
    private static final String USE_RTB = "useRiftTouchedBastion";
    private static final String SELECT_RTB_SYSTEM = "selectRiftTouchedBastionSystem_";
    private static final String RTB_RIFT = "riftTouchedBastionRift_";
    private static final String RTB_ACTIVE_PLAYER = "riftTouchedBastionActivePlayer_";

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
    public static void selectSpecterStepDestination(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (game == null || player == null || !player.hasTechReady(SS)) {
            return;
        }

        String asyncId = buttonID.substring(SELECT_SS_SHIP.length());
        List<Button> destinationButtons = getSpecterStepDestinationButtons(game, player, asyncId);
        if (destinationButtons.isEmpty()) {
            ButtonHelper.deleteMessage(event);
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentation() + " has no legal destination for _Specter Step_.");
            return;
        }

        String message = player.getRepresentation() + ", please choose the system to which to move the ship.";
        List<Button> extraButtons = List.of(Buttons.red("deleteButtons", "Decline"));
        String pagePrefix = player.factionButtonChecker() + SS_DESTINATION_PAGE + asyncId + "_";
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event, event.getMessageChannel(), destinationButtons, extraButtons, message, pagePrefix, buttonID)) {
            return;
        }
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                message,
                NewStuffHelper.buttonPagination(destinationButtons, extraButtons, pagePrefix, 25, 0, false));
    }

    @ButtonHandler(SS_DESTINATION_PAGE)
    public static void changeSpecterStepDestinationPage(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String payload = buttonID.substring(SS_DESTINATION_PAGE.length());
        int pageMarker = payload.lastIndexOf("_page");
        if (game == null || player == null || !player.hasTechReady(SS) || pageMarker < 1) {
            return;
        }
        String asyncId = payload.substring(0, pageMarker);
        List<Button> destinationButtons = getSpecterStepDestinationButtons(game, player, asyncId);
        NewStuffHelper.checkAndHandlePaginationChange(
                event,
                event.getMessageChannel(),
                destinationButtons,
                List.of(Buttons.red("deleteButtons", "Decline")),
                player.getRepresentation() + ", please choose the system to which to move the ship.",
                player.factionButtonChecker() + SS_DESTINATION_PAGE + asyncId + "_",
                buttonID);
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
        List<Button> transportButtons = getSpecterStepTransportButtons(player, source, destination);
        List<Button> extraButtons = List.of(getSpecterStepTransportDoneButton(player, source, destination));
        String transportPagePrefix = player.factionButtonChecker() + SS_TRANSPORT_PAGE + source.getPosition() + "|"
                + destination.getPosition() + "_";
        List<Button> displayedTransportButtons = new ArrayList<>(transportButtons);
        displayedTransportButtons.addAll(extraButtons);
        if (displayedTransportButtons.size() > 25) {
            displayedTransportButtons =
                    NewStuffHelper.buttonPagination(transportButtons, extraButtons, transportPagePrefix, 25, 0, false);
        }
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentation() + " moved 1 " + ship.getName() + " from "
                        + source.getRepresentationForButtons(game, player) + " to "
                        + destination.getRepresentationForButtons(game, player) + " with _Specter Step_. "
                        + "You may now move any fighters or ground forces it transports.",
                displayedTransportButtons);
    }

    @ButtonHandler(SS_TRANSPORT_PAGE)
    public static void changeSpecterStepTransportPage(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String payload = buttonID.substring(SS_TRANSPORT_PAGE.length());
        int pageMarker = payload.lastIndexOf("_page");
        if (game == null || player == null || pageMarker < 0) {
            return;
        }

        String[] positions = payload.substring(0, pageMarker).split("\\|", 2);
        if (positions.length != 2) {
            return;
        }
        Tile source = game.getTileByPosition(positions[0]);
        Tile destination = game.getTileByPosition(positions[1]);
        if (source == null || destination == null || !source.getPosition().equals(game.getActiveSystem())) {
            return;
        }

        List<Button> transportButtons = getSpecterStepTransportButtons(player, source, destination);
        List<Button> extraButtons = List.of(getSpecterStepTransportDoneButton(player, source, destination));
        String prefix = player.factionButtonChecker() + SS_TRANSPORT_PAGE + source.getPosition() + "|"
                + destination.getPosition() + "_";
        NewStuffHelper.checkAndHandlePaginationChange(
                event,
                event.getMessageChannel(),
                transportButtons,
                extraButtons,
                event.getMessage().getContentRaw(),
                prefix,
                buttonID);
    }

    @ButtonHandler(MOVE_SS_TRANSPORT)
    public static void moveSpecterStepTransport(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] parts = buttonID.substring(MOVE_SS_TRANSPORT.length()).split("\\|", 4);
        if (game == null || player == null || parts.length != 4) {
            return;
        }

        String asyncId = parts[0];
        String holderName = parts[1];
        Tile source = game.getTileByPosition(parts[2]);
        Tile destination = game.getTileByPosition(parts[3]);
        if (source == null
                || destination == null
                || !source.getPosition().equals(game.getActiveSystem())
                || source.getUnitHolders().get(holderName) == null) {
            return;
        }

        UnitKey unitKey = source.getUnitHolders().get(holderName).getUnitsByStateForPlayer(player).keySet().stream()
                .filter(key -> asyncId.equals(key.asyncID()))
                .findFirst()
                .orElse(null);
        UnitModel unit = unitKey == null ? null : player.getUnitFromUnitKey(unitKey);
        if (unit == null || !(unitKey.unitType() == UnitType.Fighter || unit.getIsGroundForce())) {
            return;
        }

        MoveUnitService.moveUnits(
                event,
                source,
                game,
                player.getColor(),
                "1 " + asyncId + " " + holderName,
                destination,
                Constants.SPACE);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentation() + " moved 1 " + unit.getName() + " to "
                        + destination.getRepresentationForButtons(game, player) + " with _Specter Step_.");
    }

    @ButtonHandler(DONE_SS_TRANSPORT)
    public static void finishSpecterStepTransport(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] positions = buttonID.substring(DONE_SS_TRANSPORT.length()).split("\\|", 2);
        if (game == null
                || player == null
                || positions.length != 2
                || !positions[0].equals(game.getActiveSystem())
                || game.getTileByPosition(positions[0]) == null
                || game.getTileByPosition(positions[1]) == null) {
            return;
        }
        ButtonHelper.deleteMessage(event);
    }

    private static List<Button> getSpecterStepTransportButtons(Player player, Tile source, Tile destination) {
        List<Button> buttons = new ArrayList<>();
        for (var holder : source.getUnitHolders().values()) {
            for (UnitKey unitKey : holder.getUnitsByStateForPlayer(player).keySet()) {
                UnitModel unit = player.getUnitFromUnitKey(unitKey);
                if (unit == null || !(unitKey.unitType() == UnitType.Fighter || unit.getIsGroundForce())) {
                    continue;
                }
                buttons.add(Buttons.green(
                        player.factionButtonChecker() + MOVE_SS_TRANSPORT + unitKey.asyncID() + "|" + holder.getName()
                                + "|" + source.getPosition() + "|" + destination.getPosition(),
                        "Move 1 " + unit.getName() + " from " + holder.getName(),
                        unitKey.unitEmoji()));
            }
        }
        return buttons;
    }

    private static List<Button> getSpecterStepDestinationButtons(Game game, Player player, String asyncId) {
        Tile source = game.getTileByPosition(game.getActiveSystem());
        UnitKey shipKey = source == null
                ? null
                : source.getSpaceUnitHolder().getUnitsByStateForPlayer(player).keySet().stream()
                        .filter(unitKey -> asyncId.equals(unitKey.asyncID()))
                        .findFirst()
                        .orElse(null);
        UnitModel ship = shipKey == null ? null : player.getUnitFromUnitKey(shipKey);
        if (ship == null || !ship.getIsShip()) return List.of();

        return game.getTileMap().values().stream()
                .filter(tile -> tile != source)
                .filter(tile -> game.getRealPlayersNDummies().stream()
                        .noneMatch(otherPlayer ->
                                otherPlayer != player && FoWHelper.playerHasActualShipsInSystem(otherPlayer, tile)))
                .map(tile -> Buttons.green(
                        player.factionButtonChecker() + MOVE_SS_SHIP + asyncId + "|" + tile.getPosition(),
                        tile.getRepresentationForButtons(game, player)))
                .toList();
    }

    private static Button getSpecterStepTransportDoneButton(Player player, Tile source, Tile destination) {
        return Buttons.red(
                player.factionButtonChecker() + DONE_SS_TRANSPORT + source.getPosition() + "|"
                        + destination.getPosition(),
                "Done Moving");
    }

    // Rift-Touched Bastion
    public static void offerRiftTouchedBastion(Game game, Tile activatedTile) {
        Player activePlayer = game.getActivePlayer();
        if (activePlayer == null) return;
        for (Player player : game.getRealPlayers()) {
            if (!player.hasTechReady(RTB)
                    || (player != activePlayer && !FoWHelper.playerHasUnitsInSystem(player, activatedTile))
                    || getRiftTouchedBastionSystemButtons(game, player, activePlayer)
                            .isEmpty()) {
                continue;
            }
            game.setStoredValue(RTB_ACTIVE_PLAYER + player.getFaction(), activePlayer.getFaction());

            MessageHelper.sendMessageToChannelWithButtons(
                    player.getCorrectChannel(),
                    player.getRepresentation()
                            + ", you may exhaust _Rift-Touched Bastion_ to treat a system containing "
                            + activePlayer.getRepresentationNoPing()
                            + "'s units as a gravity rift for this tactical action.",
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
        Player activePlayer = game.getActivePlayer();
        if (!player.hasTechReady(RTB)
                || activePlayer == null
                || !activePlayer.getFaction().equals(game.getStoredValue(RTB_ACTIVE_PLAYER + player.getFaction()))) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        List<Button> buttons = getRiftTouchedBastionSystemButtons(game, player, activePlayer);
        if (buttons.isEmpty()) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        String message = player.getRepresentation()
                + ", choose a system containing " + activePlayer.getRepresentationNoPing()
                + "'s units to treat as a gravity rift for this tactical action.";

        String prefix = player.factionButtonChecker() + SELECT_RTB_SYSTEM;
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(), message, NewStuffHelper.buttonPagination(buttons, prefix, 0));
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
    }

    @ButtonHandler(SELECT_RTB_SYSTEM)
    public static void resolveRiftTouchedBastion(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        Player activePlayer = game.getActivePlayer();
        if (!player.hasTechReady(RTB)
                || activePlayer == null
                || !activePlayer.getFaction().equals(game.getStoredValue(RTB_ACTIVE_PLAYER + player.getFaction()))) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        List<Button> buttons = getRiftTouchedBastionSystemButtons(game, player, activePlayer);
        String message = player.getRepresentationNoPing()
                + ", choose a system containing " + activePlayer.getRepresentationNoPing()
                + "'s units to treat as a gravity rift for this tactical action.";
        String prefix = player.factionButtonChecker() + SELECT_RTB_SYSTEM;

        if (NewStuffHelper.checkAndHandlePaginationChange(
                event, event.getMessageChannel(), buttons, message, prefix, buttonID)) {
            return;
        }

        String position = buttonID.substring(SELECT_RTB_SYSTEM.length());
        Tile tile = game.getTileByPosition(position);
        if (tile == null || !FoWHelper.playerHasUnitsInSystem(activePlayer, tile)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        player.exhaustTech(RTB);
        game.removeStoredValue(RTB_ACTIVE_PLAYER + player.getFaction());

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

    private static List<Button> getRiftTouchedBastionSystemButtons(Game game, Player player, Player activePlayer) {
        return game.getTileMap().values().stream()
                .filter(tile -> FoWHelper.playerHasUnitsInSystem(activePlayer, tile))
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
        game.getStoredValueMap().keySet().stream()
                .filter(key -> key.startsWith(RTB_ACTIVE_PLAYER))
                .toList()
                .forEach(game::removeStoredValue);
    }
}
