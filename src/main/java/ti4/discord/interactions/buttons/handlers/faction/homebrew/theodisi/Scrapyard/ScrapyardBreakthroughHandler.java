package ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.Scrapyard;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperAgents;
import ti4.helpers.Units.UnitKey;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;
import ti4.service.unit.RemoveUnitService.RemovedUnit;

@UtilityClass
public class ScrapyardBreakthroughHandler {
    private static final String SCRAPYARD_BT = "scrapyardbt";
    private static final String RETURN_CAPTURED = "returnScrapyardBtCaptured_";
    private static final String FINISH_RETURNING = "finishScrapyardBtReturning";

    public static void offerCompactorCapture(
            GenericInteractionCreateEvent event, Game game, List<RemovedUnit> destroyedUnits) {
        if (event == null || game == null || destroyedUnits == null || destroyedUnits.isEmpty()) {
            return;
        }
        for (Player player : game.getRealPlayers()) {
            if (!player.hasUnlockedBreakthrough(SCRAPYARD_BT)) {
                continue;
            }
            Map<UnitKey, Integer> unitsToCapture = new LinkedHashMap<>();
            for (RemovedUnit destroyedUnit : destroyedUnits) {
                if (player.unitBelongsToPlayer(destroyedUnit.unitKey()) && destroyedUnit.getTotalRemoved() > 0) {
                    unitsToCapture.merge(destroyedUnit.unitKey(), destroyedUnit.getTotalRemoved(), Integer::sum);
                }
            }
            if (unitsToCapture.isEmpty()) {
                continue;
            }
            List<String> captured = new ArrayList<>();
            for (Map.Entry<UnitKey, Integer> entry : unitsToCapture.entrySet()) {
                player.getNomboxTile().getSpaceUnitHolder().addUnit(entry.getKey(), entry.getValue());
                captured.add(entry.getValue() + " " + entry.getKey().humanReadableName());
            }
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentation() + " captured " + String.join(", ", captured) + " with _Compactor_.");
        }
    }

    public static boolean hasCapturedUnits(Player player) {
        return player != null
                && player.getNomboxTile().getSpaceUnitHolder().getUnitKeysForPlayer(player).stream()
                        .anyMatch(unitKey ->
                                player.getNomboxTile().getSpaceUnitHolder().getUnitCount(unitKey) > 0);
    }

    public static void resolveCompactorAction(GenericInteractionCreateEvent event, Game game, Player player) {
        if (!hasCapturedUnits(player) || !player.hasUnlockedBreakthrough(SCRAPYARD_BT)) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), "There are no captured units to return with _Compactor_.");
            return;
        }
        game.setStoredValue(returnedCostKey(player), "0");
        sendReturnButtons(event, game, player);
    }

    @ButtonHandler(RETURN_CAPTURED)
    public static void returnCapturedUnit(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String asyncId = buttonID.substring(RETURN_CAPTURED.length());
        UnitHolder nombox = player.getNomboxTile().getSpaceUnitHolder();
        UnitKey unitKey = nombox.getUnitKeysForPlayer(player).stream()
                .filter(key -> key.asyncID().equals(asyncId))
                .findFirst()
                .orElse(null);
        UnitModel unit = unitKey == null ? null : player.getPriorityUnitByAsyncID(unitKey.asyncID(), nombox);
        if (unitKey == null || unit == null || nombox.getUnitCount(unitKey) < 1) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        nombox.removeUnit(unitKey, 1);
        float currentCost;
        try {
            currentCost = Float.parseFloat(game.getStoredValue(returnedCostKey(player)));
        } catch (NumberFormatException e) {
            currentCost = 0;
        }
        game.setStoredValue(returnedCostKey(player), Float.toString(currentCost + unit.getCost()));
        ButtonHelper.deleteMessage(event);
        sendReturnButtons(event, game, player);
    }

    @ButtonHandler(FINISH_RETURNING)
    public static void finishReturningCapturedUnits(ButtonInteractionEvent event, Game game, Player player) {
        float returnedCost;
        try {
            returnedCost = Float.parseFloat(game.getStoredValue(returnedCostKey(player)));
        } catch (NumberFormatException e) {
            returnedCost = 0;
        }
        game.removeStoredValue(returnedCostKey(player));
        int tradeGoods = (int) Math.floor(returnedCost / 2);
        String tradeGoodMessage = player.gainTG(tradeGoods, true);
        ButtonHelperAgents.resolveArtunoCheck(player, tradeGoods);
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentation() + " returned captured units with a combined cost of "
                        + formatCost(returnedCost)
                        + " and gained " + tradeGoods + " trade good" + (tradeGoods == 1 ? "" : "s")
                        + " with _Compactor_." + tradeGoodMessage);
    }

    private static void sendReturnButtons(GenericInteractionCreateEvent event, Game game, Player player) {
        UnitHolder nombox = player.getNomboxTile().getSpaceUnitHolder();
        List<Button> buttons = new ArrayList<>();
        for (UnitKey unitKey : nombox.getUnitKeysForPlayer(player)) {
            if (nombox.getUnitCount(unitKey) < 1) {
                continue;
            }
            UnitModel unit = player.getPriorityUnitByAsyncID(unitKey.asyncID(), nombox);
            if (unit != null) {
                buttons.add(Buttons.green(
                        player.factionButtonChecker() + RETURN_CAPTURED + unitKey.asyncID(),
                        "Return 1 " + unit.getName() + " (" + formatCost(unit.getCost()) + ")",
                        unit.getUnitEmoji()));
            }
        }
        buttons.add(Buttons.red(player.factionButtonChecker() + FINISH_RETURNING, "Done"));
        float returnedCost;
        try {
            returnedCost = Float.parseFloat(game.getStoredValue(returnedCostKey(player)));
        } catch (NumberFormatException e) {
            returnedCost = 0;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentation()
                        + ", return captured units to your reinforcements with _Compactor_. Returned cost: "
                        + formatCost(returnedCost) + ".",
                buttons);
    }

    private static String returnedCostKey(Player player) {
        return "scrapyardBtReturnedCost" + player.getFaction();
    }

    private static String formatCost(float cost) {
        return cost == Math.round(cost) ? Integer.toString(Math.round(cost)) : Float.toString(cost);
    }
}
