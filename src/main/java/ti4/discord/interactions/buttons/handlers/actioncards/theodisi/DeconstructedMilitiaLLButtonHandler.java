package ti4.discord.interactions.buttons.handlers.actioncards.theodisi;

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
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;
import ti4.service.unit.AddUnitService;
import ti4.service.unit.RemoveUnitService;

@UtilityClass
public class DeconstructedMilitiaLLButtonHandler {
    private static final String RESOLVE = "resolveDeconstructedMilitia";
    private static final String REMOVE = "removeDeconstructedMilitia_";

    @ButtonHandler(RESOLVE)
    public static void resolveDeconstructedMilitia(ButtonInteractionEvent event, Game game, Player player) {
        Tile tile = game.getTileByPosition(game.getActiveSystem());
        List<Button> buttons = getEligibleShipButtons(game, player, tile);
        if (tile == null || buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentationNoPing()
                            + " has no non-fighter ship costing 4 or less in the active system.");
            ButtonHelper.deleteMessage(event);
            return;
        }
        buttons.add(Buttons.red(player.factionButtonChecker() + "deleteButtons", "Decline"));
        MessageHelper.editMessageWithButtons(
                event,
                player.getRepresentationNoPing() + ", choose a ship to remove for _Deconstructed Militia_.",
                buttons);
    }

    @ButtonHandler(REMOVE)
    public static void removeShip(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        Tile tile = game.getTileByPosition(game.getActiveSystem());
        String unitId = buttonID.substring(REMOVE.length());
        UnitKey unitKey = tile == null
                ? null
                : tile.getSpaceUnitHolder().getUnitKeysForPlayer(player).stream()
                        .filter(key -> key.asyncID().equals(unitId))
                        .findFirst()
                        .orElse(null);
        UnitModel unit =
                unitKey == null ? null : player.getPriorityUnitByAsyncID(unitKey.asyncID(), tile.getSpaceUnitHolder());
        if (unit == null || !unit.getIsShip() || unitKey.unitType() == UnitType.Fighter || unit.getCost() > 4) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event, "That ship is no longer eligible for _Deconstructed Militia_.");
            return;
        }
        int cost = Math.round(unit.getCost());
        RemoveUnitService.removeUnit(event, tile, game, player, tile.getSpaceUnitHolder(), unitKey.unitType(), 1);
        int fighters = cost * 2;
        AddUnitService.addUnits(event, tile, game, player.getColor(), fighters + " fighter");
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + " removed 1 " + unit.getName() + " and placed " + fighters
                        + " fighters in the active system for _Deconstructed Militia_.");
        ButtonHelper.deleteMessage(event);
    }

    private static List<Button> getEligibleShipButtons(Game game, Player player, Tile tile) {
        List<Button> buttons = new ArrayList<>();
        if (tile == null) return buttons;
        UnitHolder space = tile.getSpaceUnitHolder();
        for (UnitKey key : space.getUnitKeysForPlayer(player)) {
            UnitModel unit = player.getPriorityUnitByAsyncID(key.asyncID(), space);
            if (unit != null && unit.getIsShip() && key.unitType() != UnitType.Fighter && unit.getCost() <= 4) {
                buttons.add(Buttons.red(
                        player.factionButtonChecker() + REMOVE + key.asyncID(),
                        "Remove " + unit.getName(),
                        key.unitEmoji()));
            }
        }
        return buttons;
    }
}
