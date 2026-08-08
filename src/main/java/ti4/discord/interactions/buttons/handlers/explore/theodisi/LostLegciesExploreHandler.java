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
import ti4.helpers.FoWHelper;
import ti4.helpers.Units.UnitKey;
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
                "Spatailly Displace 1 Ship",
                UnitEmojis.destroyer));

        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation()
                        + ", you may gain 1 commodity or spend 1 commodity or trade good to move 1 ship to an adjacent system that contains no other player's ships.",
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
                        player.factionButtonChecker() + MOVE_SHIP + unitKey.asyncID(),
                        "Move 1 " + unitModel.getName(),
                        unitKey.unitEmoji()));
            }
        }

        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentation() + " has no ships in the active system to move.");
            return;
        }

        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentation()
                        + ", you may move 1 ship to an adjacent system that contains no other player's ships.",
                buttons);

        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(MOVE_SHIP)
    public static void spatialDisplacementStep2(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (game == null || player == null) {
            return;
        }

        String[] payload = buttonID.substring(MOVE_SHIP.length()).split("\\|", 2);
        if (payload.length != 2) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        Tile tile = game.getTileByPosition(payload[0]);
        UnitModel uM = player.getUnitFromAsyncID(payload[1]);
        if (tile == null || uM == null) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        List<Button> systems = new ArrayList<>();
        for (String adjacentSystem : FoWHelper.getAdjacentTiles(game, tile.getPosition(), player, false)) {
            if (!FoWHelper.otherPlayersHaveShipsInSystem(player, tile, game)) {
                Tile adjacentTile = game.getTileByPosition(adjacentSystem);
                systems.add(Buttons.green(
                        player.factionButtonChecker() + DISPLACE + tile.getPosition() + "|" + uM.getAsyncId() + "|"
                                + adjacentTile.getPosition(),
                        tile.getRepresentationForButtons(game, player)));
            }
        }
    }

    @ButtonHandler(DISPLACE)
    public static void spatialDisplacementFinal(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (game == null || player == null) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        String[] payload = buttonID.substring(DISPLACE.length()).split("\\|", 3);
        if (payload.length != 3) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        Tile startingTile = game.getTileByPosition(payload[0]);
        UnitModel uM = player.getUnitFromAsyncID(payload[1]);
        Tile destinationTile = game.getTileByPosition(payload[2]);
        if (startingTile == null || uM == null || destinationTile == null) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        MoveUnitService.moveUnits(
                event,
                startingTile,
                game,
                player.getColor(),
                "1 " + uM.getAsyncId(),
                destinationTile,
                "1 " + uM.getAsyncId());

        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + " moved 1 " + uM.getNameRepresentation() + " from "
                        + startingTile.getRepresentation() + " to " + destinationTile.getRepresentation()
                        + " via _Spatial Displacement_.");
    }
}
