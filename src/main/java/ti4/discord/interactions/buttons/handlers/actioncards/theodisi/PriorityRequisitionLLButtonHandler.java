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
import ti4.helpers.NewStuffHelper;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;
import ti4.service.unit.RemoveUnitService;

@UtilityClass
public class PriorityRequisitionLLButtonHandler {
    private static final String RESOLVE = "resolvePriorityRequisition";
    private static final String SOURCE = "priorityRequisitionSource_";
    private static final String SHIP = "priorityRequisitionShip_";
    private static final String STATE = "priorityRequisition_";

    @ButtonHandler(RESOLVE)
    public static void resolvePriorityRequisition(ButtonInteractionEvent event, Game game, Player player) {
        if (getDiscount(game, player) > 0) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentationNoPing()
                            + " has already resolved _Priority Requisition_ for this payment.");
            ButtonHelper.deleteMessage(event);
            return;
        }
        if (game.getStoredValue("producedUnitCostFor" + player.getFaction()).isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentationNoPing()
                            + " must resolve _Priority Requisition_ after producing and before paying.");
            ButtonHelper.deleteMessage(event);
            return;
        }
        List<Button> buttons = getSourceButtons(game, player);
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentationNoPing()
                            + " has no non-fighter ship in a system where they just produced units for _Priority Requisition_.");
            ButtonHelper.deleteMessage(event);
            return;
        }
        String message = player.getRepresentationNoPing()
                + ", choose the system where you produced units for _Priority Requisition_.";
        MessageHelper.editMessageWithButtons(
                event, message, NewStuffHelper.buttonPagination(buttons, player.factionButtonChecker() + SOURCE, 0));
    }

    @ButtonHandler(SOURCE)
    public static void selectSource(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        List<Button> buttons = getSourceButtons(game, player);
        String message = player.getRepresentationNoPing()
                + ", choose the system where you produced units for _Priority Requisition_.";
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event, event.getMessageChannel(), buttons, message, player.factionButtonChecker() + SOURCE, buttonID)) {
            return;
        }

        String sourcePosition = buttonID.substring(SOURCE.length());
        Tile source = game.getTileByPosition(sourcePosition);
        List<Button> shipButtons = getShipButtons(player, source);
        if (source == null || !getProducedSystems(player).contains(sourcePosition) || shipButtons.isEmpty()) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "That production is no longer eligible.");
            return;
        }
        String shipMessage = player.getRepresentationNoPing()
                + ", choose a non-fighter ship to return to reinforcements for _Priority Requisition_.";
        MessageHelper.editMessageWithButtons(
                event,
                shipMessage,
                NewStuffHelper.buttonPagination(
                        shipButtons, player.factionButtonChecker() + SHIP + sourcePosition + "|", 0));
    }

    @ButtonHandler(SHIP)
    public static void returnShip(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload = buttonID.substring(SHIP.length()).split("\\|", 2);
        if (payload.length != 2) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "That ship is no longer eligible.");
            return;
        }
        Tile source = game.getTileByPosition(payload[0]);
        List<Button> buttons = getShipButtons(player, source);
        String message = player.getRepresentationNoPing()
                + ", choose a non-fighter ship to return to reinforcements for _Priority Requisition_.";
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event,
                event.getMessageChannel(),
                buttons,
                message,
                player.factionButtonChecker() + SHIP + payload[0] + "|",
                buttonID)) {
            return;
        }

        UnitHolder space = source == null ? null : source.getSpaceUnitHolder();
        UnitKey unitKey = space == null
                ? null
                : space.getUnitKeysForPlayer(player).stream()
                        .filter(key -> key.asyncID().equals(payload[1]))
                        .findFirst()
                        .orElse(null);
        UnitModel unit = unitKey == null ? null : player.getPriorityUnitByAsyncID(unitKey.asyncID(), space);
        if (getDiscount(game, player) > 0
                || !getProducedSystems(player).contains(payload[0])
                || unit == null
                || !unit.getIsShip()
                || unitKey.unitType() == UnitType.Fighter) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "That ship is no longer eligible.");
            return;
        }

        int discount = (int) unit.getCost() + 2;
        RemoveUnitService.removeUnit(event, source, game, player, space, unitKey.unitType(), 1);
        game.setStoredValue(
                STATE + player.getFaction(),
                game.getStoredValue("producedUnitCostFor" + player.getFaction()) + "|" + discount);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + " returned 1 " + unit.getName()
                        + " to reinforcements for _Priority Requisition_. The combined cost of the produced units is reduced by "
                        + discount + ".");
        ButtonHelper.deleteMessage(event);
    }

    public static int getDiscount(Game game, Player player) {
        String[] state = game.getStoredValue(STATE + player.getFaction()).split("\\|", 2);
        if (state.length != 2 || !state[0].equals(game.getStoredValue("producedUnitCostFor" + player.getFaction()))) {
            return 0;
        }
        try {
            return Math.max(0, Integer.parseInt(state[1]));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static void clear(Game game, Player player) {
        game.removeStoredValue(STATE + player.getFaction());
    }

    private static List<Button> getSourceButtons(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        for (String position : getProducedSystems(player)) {
            Tile source = game.getTileByPosition(position);
            if (source != null && !getShipButtons(player, source).isEmpty()) {
                buttons.add(Buttons.gray(
                        player.factionButtonChecker() + SOURCE + position,
                        source.getRepresentationForButtons(game, player)));
            }
        }
        return buttons;
    }

    private static List<String> getProducedSystems(Player player) {
        return player.getCurrentProducedUnits().keySet().stream()
                .map(entry -> entry.split("_", 3))
                .filter(entry -> entry.length == 3)
                .map(entry -> entry[1])
                .distinct()
                .toList();
    }

    private static List<Button> getShipButtons(Player player, Tile source) {
        List<Button> buttons = new ArrayList<>();
        if (source == null) return buttons;
        UnitHolder space = source.getSpaceUnitHolder();
        for (UnitKey key : space.getUnitKeysForPlayer(player)) {
            UnitModel unit = player.getPriorityUnitByAsyncID(key.asyncID(), space);
            if (unit != null && unit.getIsShip() && key.unitType() != UnitType.Fighter) {
                buttons.add(Buttons.red(
                        player.factionButtonChecker() + SHIP + source.getPosition() + "|" + key.asyncID(),
                        "Return " + unit.getName(),
                        key.unitEmoji()));
            }
        }
        return buttons;
    }
}
