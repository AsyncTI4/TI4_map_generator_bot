package ti4.discord.interactions.buttons.handlers.actioncards.acd2;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.Units.UnitKey;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;
import ti4.service.unit.AddUnitService;
import ti4.service.unit.RemoveUnitService;
import ti4.service.unit.RemoveUnitService.RemovedUnit;

@UtilityClass
class CovertOperationAcd2ButtonHandler {

    @ButtonHandler("resolveCovertOperation")
    public static void resolveCovertOperation(Player player, Game game, ButtonInteractionEvent event) {
        List<Button> buttons = getCovertOperationTileButtons(game, player);
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationUnfogged()
                            + ", you have no ships or ground forces on the game board to use for _Covert Operation_.");
            ButtonHelper.deleteMessage(event);
            return;
        }
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged()
                        + ", please choose the system containing the unit you wish to reposition for _Covert Operation_.",
                buttons);
    }

    @ButtonHandler("covertOpTile_")
    public static void resolveCovertOperationTileSelection(
            Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String pos = buttonID.replace("covertOpTile_", "");
        Tile tile = game.getTileByPosition(pos);
        if (tile == null) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "Could not find that system.");
            ButtonHelper.deleteMessage(event);
            return;
        }
        List<Button> buttons = getCovertOperationUnitButtons(player, tile);
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationUnfogged()
                            + ", no eligible units found in that system for _Covert Operation_.");
            ButtonHelper.deleteMessage(event);
            return;
        }
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged()
                        + ", please choose the unit to place in the active system for _Covert Operation_.",
                buttons);
    }

    @ButtonHandler("covertOpUnit_")
    public static void resolveCovertOperationUnitSelection(
            Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        // buttonID format: covertOpUnit_<position>_<holder>_<unitName>[damaged]
        String stripped = buttonID.replace("covertOpUnit_", "");
        String[] parts = stripped.split("_", 3);
        if (parts.length < 3) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "Could not resolve _Covert Operation_.");
            ButtonHelper.deleteMessage(event);
            return;
        }
        String pos = parts[0];
        String holder = parts[1];
        String unitName = parts[2];

        String activePos = game.getActiveSystem();
        if (activePos == null || activePos.isBlank()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationUnfogged()
                            + ", there is no active system. _Covert Operation_ cannot be completed.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        Tile sourceTile = game.getTileByPosition(pos);
        Tile activeTile = game.getTileByPosition(activePos);
        if (sourceTile == null || activeTile == null) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "Could not resolve _Covert Operation_.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        boolean damaged = unitName.endsWith("damaged");
        if (damaged) unitName = unitName.replace("damaged", "");

        String unitSpec = Constants.SPACE.equals(holder) ? "1 " + unitName : "1 " + unitName + " " + holder;
        List<RemovedUnit> removed =
                RemoveUnitService.removeUnits(event, sourceTile, game, player.getColor(), unitSpec, damaged);
        if (!removed.isEmpty()) {
            String unitEmoji = removed.getFirst().unitKey().unitEmoji().toString();
            AddUnitService.addUnits(event, activeTile, game, player.getColor(), "1 " + unitName, removed);
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationUnfogged() + " moved " + unitEmoji + " from "
                            + sourceTile.getRepresentationForButtons(game, player) + " to the space area of "
                            + activeTile.getRepresentationForButtons(game, player)
                            + " via _Covert Operation_.");
        } else {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationUnfogged() + " could not remove the unit for _Covert Operation_.");
        }
        ButtonHelper.deleteMessage(event);
    }

    private static List<Button> getCovertOperationTileButtons(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : game.getTileMap().values()) {
            if (tileHasPlayerMovableUnit(player, tile)) {
                buttons.add(Buttons.gray(
                        "covertOpTile_" + tile.getPosition(), tile.getRepresentationForButtons(game, player)));
            }
        }
        return buttons;
    }

    private static boolean tileHasPlayerMovableUnit(Player player, Tile tile) {
        for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
            for (UnitKey unitKey : unitHolder.getUnits().keySet()) {
                if (!player.unitBelongsToPlayer(unitKey)) continue;
                UnitModel model = player.getUnitFromUnitKey(unitKey);
                if (model != null && (model.getIsShip() || model.getIsGroundForce())) return true;
            }
        }
        return false;
    }

    private static List<Button> getCovertOperationUnitButtons(Player player, Tile tile) {
        List<Button> buttons = new ArrayList<>();
        String factionChecker = player.factionButtonChecker();
        for (Map.Entry<String, UnitHolder> holderEntry : tile.getUnitHolders().entrySet()) {
            String holderName = holderEntry.getKey();
            UnitHolder unitHolder = holderEntry.getValue();
            for (Map.Entry<UnitKey, Integer> unitEntry : new HashMap<>(unitHolder.getUnits()).entrySet()) {
                UnitKey unitKey = unitEntry.getKey();
                if (!player.unitBelongsToPlayer(unitKey)) continue;
                UnitModel unitModel = player.getUnitFromUnitKey(unitKey);
                if (unitModel == null || (!unitModel.getIsShip() && !unitModel.getIsGroundForce())) continue;

                String prettyName = unitModel.getName();
                String unitName = unitKey.unitName();
                int totalUnits = unitEntry.getValue();
                int damagedUnits = 0;
                if (unitHolder.getUnitDamage() != null) {
                    damagedUnits = unitHolder.getUnitDamage().getOrDefault(unitKey, 0);
                }

                for (int x = 1; x <= damagedUnits && x < 2; x++) {
                    buttons.add(Buttons.red(
                            factionChecker + "covertOpUnit_" + tile.getPosition() + "_" + holderName + "_" + unitName
                                    + "damaged",
                            "Move A Damaged " + prettyName,
                            unitKey.unitEmoji()));
                }
                totalUnits -= damagedUnits;
                for (int x = 1; x <= totalUnits && x < 2; x++) {
                    buttons.add(Buttons.gray(
                            factionChecker + "covertOpUnit_" + tile.getPosition() + "_" + holderName + "_" + unitName,
                            "Move " + prettyName,
                            unitKey.unitEmoji()));
                }
            }
        }
        return buttons;
    }
}
