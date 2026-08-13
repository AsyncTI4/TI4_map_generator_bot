package ti4.discord.interactions.buttons.handlers.actioncards.theodisi;

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
import ti4.helpers.AliasHandler;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.FoWHelper;
import ti4.helpers.NewStuffHelper;
import ti4.helpers.Units.UnitKey;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;
import ti4.service.unit.MoveUnitService;

@UtilityClass
public class ForwardAssemblyLLButtonHandler {
    private static final String RESOLVE = "resolveForwardAssembly";
    private static final String SOURCE = "forwardAssemblySource_";
    private static final String DESTINATION = "forwardAssemblyDestination_";
    private static final String MOVE = "forwardAssemblyMove_";
    private static final String DONE = "finishForwardAssembly";
    private static final String STATE = "forwardAssembly_";

    @ButtonHandler(RESOLVE)
    public static void resolveForwardAssembly(ButtonInteractionEvent event, Game game, Player player) {
        List<Button> buttons = getSourceButtons(game, player);
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentationNoPing() + " has no produced units eligible for _Forward Assembly_.");
            ButtonHelper.deleteMessage(event);
            return;
        }
        String message = player.getRepresentationNoPing()
                + ", choose the system where you produced units for _Forward Assembly_.";
        MessageHelper.editMessageWithButtons(
                event, message, NewStuffHelper.buttonPagination(buttons, player.factionButtonChecker() + SOURCE, 0));
    }

    @ButtonHandler(SOURCE)
    public static void selectSource(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        List<Button> buttons = getSourceButtons(game, player);
        String message = player.getRepresentationNoPing()
                + ", choose the system where you produced units for _Forward Assembly_.";
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event, event.getMessageChannel(), buttons, message, player.factionButtonChecker() + SOURCE, buttonID)) {
            return;
        }

        String sourcePosition = buttonID.substring(SOURCE.length());
        Tile source = game.getTileByPosition(sourcePosition);
        if (source == null || getProducedUnitEntries(player, sourcePosition).isEmpty()) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "That production is no longer eligible.");
            return;
        }
        List<Button> destinationButtons = getDestinationButtons(game, player, source);
        if (destinationButtons.isEmpty()) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event, "There is no eligible adjacent destination for those produced units.");
            return;
        }
        String destinationMessage = player.getRepresentationNoPing()
                + ", choose an adjacent system containing your ships and no other players' ships for _Forward Assembly_.";
        MessageHelper.editMessageWithButtons(
                event,
                destinationMessage,
                NewStuffHelper.buttonPagination(
                        destinationButtons, player.factionButtonChecker() + DESTINATION + sourcePosition + "|", 0));
    }

    @ButtonHandler(DESTINATION)
    public static void selectDestination(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload = buttonID.substring(DESTINATION.length()).split("\\|", 2);
        if (payload.length != 2) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "That destination is no longer eligible.");
            return;
        }
        String sourcePosition = payload[0];
        Tile source = game.getTileByPosition(sourcePosition);
        List<Button> buttons = getDestinationButtons(game, player, source);
        String message = player.getRepresentationNoPing()
                + ", choose an adjacent system containing your ships and no other players' ships for _Forward Assembly_.";
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event,
                event.getMessageChannel(),
                buttons,
                message,
                player.factionButtonChecker() + DESTINATION + sourcePosition + "|",
                buttonID)) {
            return;
        }

        Tile destination = game.getTileByPosition(payload[1]);
        if (source == null
                || destination == null
                || getProducedUnitEntries(player, sourcePosition).isEmpty()
                || !isEligibleDestination(game, player, source, destination)) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "That destination is no longer eligible.");
            return;
        }
        game.setStoredValue(STATE + player.getFaction(), sourcePosition + "|" + destination.getPosition());
        sendMoveButtons(event, game, player);
    }

    @ButtonHandler(MOVE)
    public static void moveProducedUnit(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] state = game.getStoredValue(STATE + player.getFaction()).split("\\|", 3);
        Tile source = state.length >= 2 ? game.getTileByPosition(state[0]) : null;
        Tile destination = state.length >= 2 ? game.getTileByPosition(state[1]) : null;
        if (source == null || destination == null || !isEligibleDestination(game, player, source, destination)) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "This _Forward Assembly_ placement has expired.");
            return;
        }
        Map<String, Integer> movedUnits = state.length == 3 ? decodeMovedUnits(state[2]) : new HashMap<>();
        List<Button> buttons = getMoveButtons(game, player, source, movedUnits);
        String message = getMoveMessage(player, destination);
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event,
                event.getMessageChannel(),
                buttons,
                List.of(Buttons.red(player.factionButtonChecker() + DONE, "Done Moving")),
                message,
                player.factionButtonChecker() + MOVE,
                buttonID)) {
            return;
        }

        String producedEntry = buttonID.substring(MOVE.length());
        String[] entry = producedEntry.split("_", 3);
        if (entry.length != 3
                || !getProducedUnitEntries(player, source.getPosition()).contains(producedEntry)) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "That produced unit is no longer eligible.");
            return;
        }
        UnitKey unitKey = Mapper.getUnitKey(AliasHandler.resolveUnit(entry[0]), player.getColor());
        UnitHolder holder = source.getUnitHolders().get(entry[2]);
        int producedCount = player.getCurrentProducedUnits().getOrDefault(producedEntry, 0);
        if (unitKey == null
                || holder == null
                || movedUnits.getOrDefault(producedEntry, 0) >= producedCount
                || holder.getUnitCount(unitKey) < 1) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "That produced unit is no longer eligible.");
            return;
        }

        String unitList = "1 " + AliasHandler.resolveUnit(entry[0]);
        if (!Constants.SPACE.equals(entry[2])) unitList += " " + entry[2];
        MoveUnitService.moveUnits(event, source, game, player.getColor(), unitList, destination, Constants.SPACE);
        movedUnits.merge(producedEntry, 1, Integer::sum);
        game.setStoredValue(
                STATE + player.getFaction(), state[0] + "|" + state[1] + "|" + encodeMovedUnits(movedUnits));
        sendMoveButtons(event, game, player);
    }

    @ButtonHandler(DONE)
    public static void finishForwardAssembly(ButtonInteractionEvent event, Game game, Player player) {
        game.removeStoredValue(STATE + player.getFaction());
        ButtonHelper.deleteMessage(event);
    }

    private static void sendMoveButtons(ButtonInteractionEvent event, Game game, Player player) {
        String[] state = game.getStoredValue(STATE + player.getFaction()).split("\\|", 3);
        Tile source = state.length >= 2 ? game.getTileByPosition(state[0]) : null;
        Tile destination = state.length >= 2 ? game.getTileByPosition(state[1]) : null;
        if (source == null || destination == null) return;
        Map<String, Integer> movedUnits = state.length == 3 ? decodeMovedUnits(state[2]) : new HashMap<>();
        List<Button> buttons = getMoveButtons(game, player, source, movedUnits);
        MessageHelper.editMessageWithButtons(
                event, getMoveMessage(player, destination), getMovePageButtons(player, buttons));
    }

    private static List<Button> getSourceButtons(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        for (String position : player.getCurrentProducedUnits().keySet().stream()
                .map(entry -> entry.split("_", 3))
                .filter(entry -> entry.length == 3)
                .map(entry -> entry[1])
                .distinct()
                .toList()) {
            Tile tile = game.getTileByPosition(position);
            if (tile != null && !getDestinationButtons(game, player, tile).isEmpty()) {
                buttons.add(Buttons.gray(
                        player.factionButtonChecker() + SOURCE + position,
                        tile.getRepresentationForButtons(game, player)));
            }
        }
        return buttons;
    }

    private static List<Button> getDestinationButtons(Game game, Player player, Tile source) {
        List<Button> buttons = new ArrayList<>();
        if (source == null) return buttons;
        for (String position : FoWHelper.getAdjacentTilesAndNotThisTile(game, source.getPosition(), player, false)) {
            Tile destination = game.getTileByPosition(position);
            if (destination != null && isEligibleDestination(game, player, source, destination)) {
                buttons.add(Buttons.gray(
                        player.factionButtonChecker() + DESTINATION + source.getPosition() + "|" + position,
                        destination.getRepresentationForButtons(game, player)));
            }
        }
        return buttons;
    }

    private static List<Button> getMoveButtons(Game game, Player player, Tile source, Map<String, Integer> movedUnits) {
        List<Button> buttons = new ArrayList<>();
        for (String producedEntry : getProducedUnitEntries(player, source.getPosition())) {
            String[] entry = producedEntry.split("_", 3);
            UnitKey unitKey = Mapper.getUnitKey(AliasHandler.resolveUnit(entry[0]), player.getColor());
            UnitHolder holder = source.getUnitHolders().get(entry[2]);
            if (unitKey == null
                    || holder == null
                    || movedUnits.getOrDefault(producedEntry, 0)
                            >= player.getCurrentProducedUnits().getOrDefault(producedEntry, 0)
                    || holder.getUnitCount(unitKey) < 1) {
                continue;
            }
            UnitModel unit = player.getPriorityUnitByAsyncID(unitKey.asyncID(), holder);
            String label = "Move 1 " + (unit == null ? unitKey.humanReadableName() : unit.getName());
            if (!Constants.SPACE.equals(entry[2])) label += " from " + entry[2];
            buttons.add(
                    Buttons.green(player.factionButtonChecker() + MOVE + producedEntry, label, unitKey.unitEmoji()));
        }
        return buttons;
    }

    private static List<Button> getMovePageButtons(Player player, List<Button> buttons) {
        Button done = Buttons.red(player.factionButtonChecker() + DONE, "Done Moving");
        List<Button> page = NewStuffHelper.buttonPagination(
                buttons, List.of(done), player.factionButtonChecker() + MOVE, 25, 0, false);
        if (!page.contains(done)) {
            page = new ArrayList<>(page);
            page.add(done);
        }
        return page;
    }

    private static List<String> getProducedUnitEntries(Player player, String sourcePosition) {
        return player.getCurrentProducedUnits().keySet().stream()
                .filter(entry -> {
                    String[] parts = entry.split("_", 3);
                    return parts.length == 3 && sourcePosition.equals(parts[1]);
                })
                .toList();
    }

    private static boolean isEligibleDestination(Game game, Player player, Tile source, Tile destination) {
        return FoWHelper.getAdjacentTilesAndNotThisTile(game, source.getPosition(), player, false)
                        .contains(destination.getPosition())
                && FoWHelper.playerHasActualShipsInSystem(player, destination)
                && game.getRealPlayers().stream()
                        .filter(otherPlayer -> otherPlayer != player)
                        .noneMatch(otherPlayer -> FoWHelper.playerHasActualShipsInSystem(otherPlayer, destination));
    }

    private static String getMoveMessage(Player player, Tile destination) {
        return player.getRepresentationNoPing() + ", move any number of the units you just produced to "
                + destination.getRepresentationForButtons() + " for _Forward Assembly_.";
    }

    private static Map<String, Integer> decodeMovedUnits(String encodedMovedUnits) {
        Map<String, Integer> movedUnits = new HashMap<>();
        if (encodedMovedUnits == null || encodedMovedUnits.isBlank()) return movedUnits;
        for (String entry : encodedMovedUnits.split(";")) {
            String[] parts = entry.split("=", 2);
            if (parts.length != 2) continue;
            try {
                movedUnits.put(parts[0], Integer.parseInt(parts[1]));
            } catch (NumberFormatException ignored) {
                // Ignore malformed stale state.
            }
        }
        return movedUnits;
    }

    private static String encodeMovedUnits(Map<String, Integer> movedUnits) {
        return movedUnits.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .reduce((first, second) -> first + ";" + second)
                .orElse("");
    }
}
