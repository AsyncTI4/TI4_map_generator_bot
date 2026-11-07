package ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.choir;

import java.util.ArrayList;
import java.util.Comparator;
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
import ti4.helpers.thundersedge.BreakthroughCommandHelper;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;
import ti4.service.emoji.FactionEmojis;

@UtilityClass
public class ChoirBreakthroughHandler {
    private static final String CENOTAPH_CIRCUIT = "choirbt";
    private static final String START = "choirCenotaphCircuit";
    private static final String SYSTEM = "choirCenotaphSystem_";
    private static final String UNIT = "choirCenotaphUnit_";

    public static Button getCenotaphCircuitButton(Game game, Player player) {
        if (!canUseCenotaphCircuit(game, player)) {
            return null;
        }
        return Buttons.gray(player.factionButtonChecker() + START, "Use Cenotaph Circuit", FactionEmojis.choir);
    }

    @ButtonHandler(START)
    public static void startCenotaphCircuit(ButtonInteractionEvent event, Game game, Player player) {
        if (!canUseCenotaphCircuit(game, player)) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        sendSystemButtons(event, game, player, 0);
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
    }

    @ButtonHandler(SYSTEM)
    public static void selectCenotaphCircuitSystem(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        List<Tile> systems = getEligibleSystems(game, player);
        String message = player.getRepresentationNoPing() + ", choose a system for _Cenotaph Circuit_.";
        List<Button> buttons = getSystemButtons(game, player, systems);
        String prefix = player.factionButtonChecker() + SYSTEM;
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event, event.getMessageChannel(), buttons, message, prefix, buttonID)) {
            return;
        }

        Tile tile = game.getTileByPosition(buttonID.substring(SYSTEM.length()));
        if (tile == null || !systems.contains(tile)) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        sendUnitButtons(event, game, player, tile);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(UNIT)
    public static void resolveCenotaphCircuitUnit(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] values = buttonID.substring(UNIT.length()).split("\\|", 4);
        Tile tile = values.length == 4 ? game.getTileByPosition(values[0]) : null;
        List<Button> buttons = tile == null ? List.of() : getUnitButtons(player, tile);
        String message = tile == null
                ? "Choose a unit for _Cenotaph Circuit_."
                : player.getRepresentationNoPing() + ", choose a unit in "
                        + tile.getRepresentationForButtons(game, player) + " for _Cenotaph Circuit_.";
        String prefix = values.length < 1
                ? player.factionButtonChecker() + UNIT
                : player.factionButtonChecker() + UNIT + values[0] + "|";
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event, event.getMessageChannel(), buttons, message, prefix, buttonID)) {
            return;
        }

        UnitHolder unitHolder = tile == null ? null : tile.getUnitHolders().get(values[1]);
        UnitKey unitKey = values.length == 4 ? findUnitKey(unitHolder, player, values[2]) : null;
        UnitModel unit = unitKey == null ? null : player.getUnitFromUnitKey(unitKey);
        boolean damaged = values.length == 4 && "damaged".equals(values[3]);
        if (!player.hasReadyBreakthrough(CENOTAPH_CIRCUIT)
                || unitHolder == null
                || unit == null
                || !isEligibleState(player, unitHolder, unitKey, unit, damaged)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        BreakthroughCommandHelper.exhaustBreakthrough(player, CENOTAPH_CIRCUIT);
        if (damaged) {
            unitHolder.removeDamagedUnit(unitKey, 1);
        } else {
            unitHolder.addDamagedUnit(unitKey, 1);
        }
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + " " + (damaged ? "repaired" : "damaged") + " 1 " + unit.getName()
                        + " with _Cenotaph Circuit_.");
        ButtonHelper.deleteMessage(event);
    }

    private static boolean canUseCenotaphCircuit(Game game, Player player) {
        return game != null
                && player != null
                && player.hasReadyBreakthrough(CENOTAPH_CIRCUIT)
                && !getEligibleSystems(game, player).isEmpty();
    }

    private static List<Tile> getEligibleSystems(Game game, Player player) {
        return game.getTileMap().values().stream()
                .filter(tile -> !getUnitButtons(player, tile).isEmpty())
                .sorted(Comparator.comparing(Tile::getPosition))
                .toList();
    }

    private static List<Button> getSystemButtons(Game game, Player player, List<Tile> systems) {
        return systems.stream()
                .map(tile -> Buttons.gray(
                        player.factionButtonChecker() + SYSTEM + tile.getPosition(),
                        tile.getRepresentationForButtons(game, player)))
                .toList();
    }

    private static void sendSystemButtons(ButtonInteractionEvent event, Game game, Player player, int page) {
        String message = player.getRepresentationNoPing() + ", choose a system for _Cenotaph Circuit_.";
        List<Button> buttons = getSystemButtons(game, player, getEligibleSystems(game, player));
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                message,
                NewStuffHelper.buttonPagination(buttons, player.factionButtonChecker() + SYSTEM, page));
    }

    private static void sendUnitButtons(ButtonInteractionEvent event, Game game, Player player, Tile tile) {
        String message = player.getRepresentationNoPing() + ", choose a unit in "
                + tile.getRepresentationForButtons(game, player) + " for _Cenotaph Circuit_.";
        List<Button> buttons = getUnitButtons(player, tile);
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                message,
                NewStuffHelper.buttonPagination(
                        buttons, player.factionButtonChecker() + UNIT + tile.getPosition() + "|", 0));
    }

    private static List<Button> getUnitButtons(Player player, Tile tile) {
        List<Button> buttons = new ArrayList<>();
        for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
            for (UnitKey unitKey : unitHolder.getUnitKeys()) {
                if (!player.unitBelongsToPlayer(unitKey)) {
                    continue;
                }
                UnitModel unit = player.getUnitFromUnitKey(unitKey);
                if (unit == null) {
                    continue;
                }
                if (unitHolder.getDamagedUnitCount(unitKey) > 0) {
                    buttons.add(getUnitButton(player, tile, unitHolder, unitKey, unit, true));
                }
                if (isCenotaphSustainUnit(unit)
                        && unitHolder.getUnitCount(unitKey) > unitHolder.getDamagedUnitCount(unitKey)) {
                    buttons.add(getUnitButton(player, tile, unitHolder, unitKey, unit, false));
                }
            }
        }
        buttons.sort(Comparator.comparing(Button::getLabel));
        return buttons;
    }

    private static Button getUnitButton(
            Player player, Tile tile, UnitHolder unitHolder, UnitKey unitKey, UnitModel unit, boolean damaged) {
        return Buttons.gray(
                player.factionButtonChecker() + UNIT + tile.getPosition() + "|" + unitHolder.getName() + "|"
                        + unitKey.asyncID() + "|" + (damaged ? "damaged" : "undamaged"),
                unit.getName() + " (" + (damaged ? "Damaged" : "Undamaged") + ")");
    }

    private static UnitKey findUnitKey(UnitHolder unitHolder, Player player, String asyncID) {
        if (unitHolder == null) {
            return null;
        }
        return unitHolder.getUnitKeys().stream()
                .filter(player::unitBelongsToPlayer)
                .filter(unitKey -> unitKey.asyncID().equals(asyncID))
                .findFirst()
                .orElse(null);
    }

    private static boolean isEligibleState(
            Player player, UnitHolder unitHolder, UnitKey unitKey, UnitModel unit, boolean damaged) {
        if (damaged) {
            return unitHolder.getDamagedUnitCount(unitKey) > 0;
        }
        return isCenotaphSustainUnit(unit)
                && unitHolder.getUnitCount(unitKey) > unitHolder.getDamagedUnitCount(unitKey);
    }

    private static boolean isCenotaphSustainUnit(UnitModel unit) {
        return Boolean.TRUE.equals(unit.getSustainDamage())
                || (Boolean.TRUE.equals(unit.getIsShip()) && unit.getCapacityValue() > 0);
    }
}
