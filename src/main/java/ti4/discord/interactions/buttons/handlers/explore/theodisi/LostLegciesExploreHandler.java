package ti4.discord.interactions.buttons.handlers.explore.theodisi;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.FoWHelper;
import ti4.helpers.Units.UnitKey;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;
import ti4.service.emoji.MiscEmojis;
import ti4.service.emoji.UnitEmojis;
import ti4.service.unit.MoveUnitService;

@UtilityClass
public class LostLegciesExploreHandler {
    private static final String USE_SPATIAL = "useSpatialDisplacement_";
    private static final String MOVE_SHIP = "moveSpatialDisplacementShip_";
    private static final String DISPLACE = "finalizeSpatialDisplacementShipMovement_";

    public static void resolveSpatialDisplacement(
            GenericInteractionCreateEvent event, Game game, Player player, Tile tile) {
        if (game == null || player == null) {
            return;
        }

        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.green(player.factionButtonChecker() + "gainComms_1", "Gain 1 Commodity", MiscEmojis.comm));
        buttons.add(Buttons.green(
                player.factionButtonChecker() + USE_SPATIAL + tile.getPosition(),
                "Use Spatial Displacement",
                UnitEmojis.destroyer));

        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation()
                        + ", you may gain 1 commodity or spend 1 commodity or trade good to move 1 ship to an adjacent system that contains no other player's ships using _Spatial Displacement_.",
                buttons);
    }

    @ButtonHandler(USE_SPATIAL)
    public static void spatialDisplacementStep1(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (game == null || player == null) {
            return;
        }

        String tilePos = buttonID.replace(USE_SPATIAL, "");

        Tile activeTile = game.getTileByPosition(tilePos);
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
                        player.factionButtonChecker() + MOVE_SHIP + unitKey.asyncID() + "|" + activeTile.getPosition(),
                        "Move 1 " + unitModel.getName(),
                        unitKey.unitEmoji()));
            }
        }

        ButtonHelper.deleteMessage(event);
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentation() + " has no ships in the active system to move.");
            return;
        }

        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentation() + ", please choose the ship you wish to move using _Spatial Displacement_.",
                buttons);
    }

    @ButtonHandler(MOVE_SHIP)
    public static void spatialDisplacementStep2(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (game == null || player == null) {
            return;
        }

        String[] payload = buttonID.substring(MOVE_SHIP.length()).split("\\|", 2);
        if (payload.length != 2) {
            return;
        }

        String asyncId = payload[0];
        String tilePos = payload[1];
        Tile tile = game.getTileByPosition(tilePos);
        if (asyncId == null) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Could not find that ship.");
            ButtonHelper.deleteMessage(event);
            return;
        }
        if (tile == null) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Could not find that system.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        List<Button> destinations = new ArrayList<>();
        for (String adjacent : FoWHelper.getAdjacentTiles(game, tile.getPosition(), player, false)) {
            Tile adjacentTile = game.getTileByPosition(adjacent);
            if (adjacentTile == null || FoWHelper.otherPlayersHaveShipsInSystem(player, adjacentTile, game)) {
                continue;
            }

            destinations.add(Buttons.green(
                    player.factionButtonChecker() + DISPLACE + tile.getPosition() + "|" + asyncId + "|"
                            + adjacentTile.getPosition(),
                    adjacentTile.getRepresentationForButtons(game, player)));
        }
        if (destinations.isEmpty()) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "There are no eligible adjacent systems.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + ", please choose the system to which you wish to move the ship.",
                destinations);

        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(DISPLACE)
    public static void resolveSpatialDisplacement(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (game == null || player == null) {
            return;
        }

        String payload = buttonID.replace(DISPLACE, "");
        String[] parts = payload.split("\\|", 3);
        if (parts.length != 3) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Could not resolve that movement selection.");
            ButtonHelper.deleteMessage(event);
            return;
        }
        String origPos = parts[0];
        String asyncId = parts[1];
        String destPos = parts[2];

        Tile origTile = game.getTileByPosition(origPos);
        UnitModel unit = player.getUnitFromAsyncID(asyncId);
        UnitKey unitKey = Mapper.getUnitKey(asyncId, player.getColorID());
        Tile destTile = game.getTileByPosition(destPos);
        if (origTile == null || destTile == null) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), "Could not find original or destination tile.");
            ButtonHelper.deleteMessage(event);
            return;
        }
        if (unit == null || unitKey == null || origTile.getSpaceUnitHolder().getUnitCount(unitKey) < 1) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Could not find that ship.");
            ButtonHelper.deleteMessage(event);
            return;
        }
        if (!FoWHelper.getAdjacentTiles(game, origPos, player, false).contains(destPos)
                || FoWHelper.otherPlayersHaveShipsInSystem(player, destTile, game)) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "That system is no longer eligible.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        MoveUnitService.moveUnits(event, origTile, game, player.getColor(), "1 " + asyncId, destTile, Constants.SPACE);

        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentation() + " moved 1 " + unit.getNameRepresentation() + " from "
                        + origTile.getRepresentation() + " to " + destTile.getRepresentation()
                        + " via _Spatial Displacement_.");

        ButtonHelper.deleteMessage(event);
    }
}
