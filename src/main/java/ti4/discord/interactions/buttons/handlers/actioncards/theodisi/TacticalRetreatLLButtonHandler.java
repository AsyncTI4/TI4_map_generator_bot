package ti4.discord.interactions.buttons.handlers.actioncards.theodisi;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.FoWHelper;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;
import ti4.service.unit.MoveUnitService;

@UtilityClass
public class TacticalRetreatLLButtonHandler {
    private static final String SELECTED_SHIPS = "tacticalRetreatShips_";
    private static final String SELECTED_CARGO = "tacticalRetreatCargo_";

    @ButtonHandler("resolveTacticalRetreat")
    public static void resolveTacticalRetreat(ButtonInteractionEvent event, Game game, Player player) {
        Tile activeSystem = game.getTileByPosition(game.getActiveSystem());
        if (activeSystem == null || getEligibleShips(player, activeSystem).isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentation() + ", you have no eligible non-fighter ships in the active system.");
            return;
        }
        game.removeStoredValue(SELECTED_SHIPS + player.getFaction());
        game.removeStoredValue(SELECTED_CARGO + player.getFaction());
        sendShipButtons(event, game, player, activeSystem);
    }

    @ButtonHandler("tacticalRetreatShip_")
    public static void selectShip(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload = buttonID.substring("tacticalRetreatShip_".length()).split("\\|", 2);
        if (payload.length != 2) return;
        Tile source = game.getTileByPosition(payload[0]);
        UnitKey unitKey = getUnitKey(player, source, payload[1]);
        if (source == null
                || unitKey == null
                || !getEligibleShips(player, source).contains(unitKey)) return;

        List<String> selected = getSelection(game, SELECTED_SHIPS, player);
        if (selected.size() >= 2
                || selected.stream().filter(payload[1]::equals).count()
                        >= source.getSpaceUnitHolder().getUnitCount(unitKey)) {
            return;
        }
        selected.add(payload[1]);
        storeSelection(game, SELECTED_SHIPS, player, selected);
        sendShipButtons(event, game, player, source);
    }

    @ButtonHandler("cancelTacticalRetreat")
    public static void cancelTacticalRetreat(ButtonInteractionEvent event, Game game, Player player) {
        game.removeStoredValue(SELECTED_SHIPS + player.getFaction());
        game.removeStoredValue(SELECTED_CARGO + player.getFaction());
        ButtonHelper.deleteMessage(event);
    }

    public static void clearTacticalRetreat(Game game) {
        for (Player player : game.getRealPlayers()) {
            game.removeStoredValue(SELECTED_SHIPS + player.getFaction());
            game.removeStoredValue(SELECTED_CARGO + player.getFaction());
        }
    }

    @ButtonHandler("tacticalRetreatDestination_")
    public static void selectDestination(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        Tile source = game.getTileByPosition(buttonID.substring("tacticalRetreatDestination_".length()));
        if (source == null || getSelection(game, SELECTED_SHIPS, player).isEmpty()) return;
        List<Button> buttons = new ArrayList<>();
        for (String position : FoWHelper.getAdjacentTiles(game, source.getPosition(), player, false)) {
            Tile destination = game.getTileByPosition(position);
            if (destination != null && !FoWHelper.otherPlayersHaveShipsInSystem(player, destination, game)) {
                buttons.add(Buttons.green(
                        player.factionButtonChecker() + "tacticalRetreatMoveTo_" + source.getPosition() + "|"
                                + position,
                        "Move to " + destination.getRepresentationForButtons(game, player)));
            }
        }
        MessageHelper.editMessageWithButtons(
                event,
                player.getRepresentation() + ", choose an adjacent system containing no other player's ships.",
                appendCancelButton(player, buttons));
    }

    @ButtonHandler("tacticalRetreatMoveTo_")
    public static void moveToDestination(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload = buttonID.substring("tacticalRetreatMoveTo_".length()).split("\\|", 2);
        if (payload.length != 2) return;
        Tile source = game.getTileByPosition(payload[0]);
        Tile destination = game.getTileByPosition(payload[1]);
        if (source == null
                || destination == null
                || getSelection(game, SELECTED_SHIPS, player).isEmpty()) return;
        if (!FoWHelper.getAdjacentTiles(game, source.getPosition(), player, false)
                        .contains(destination.getPosition())
                || FoWHelper.otherPlayersHaveShipsInSystem(player, destination, game)) return;
        sendCargoButtons(event, game, player, source, destination);
    }

    @ButtonHandler("tacticalRetreatCargo_")
    public static void selectCargo(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload = buttonID.substring("tacticalRetreatCargo_".length()).split("\\|", 3);
        if (payload.length != 3) return;
        Tile source = game.getTileByPosition(payload[0]);
        Tile destination = game.getTileByPosition(payload[1]);
        UnitKey unitKey = getUnitKey(player, source, payload[2]);
        if (source == null || destination == null || unitKey == null || !isCargo(player, source, unitKey)) return;

        List<String> selected = getSelection(game, SELECTED_CARGO, player);
        if (selected.stream().filter(payload[2]::equals).count()
                >= source.getSpaceUnitHolder().getUnitCount(unitKey)) return;
        selected.add(payload[2]);
        storeSelection(game, SELECTED_CARGO, player, selected);
        sendCargoButtons(event, game, player, source, destination);
    }

    @ButtonHandler("finishTacticalRetreat_")
    public static void finishTacticalRetreat(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload = buttonID.substring("finishTacticalRetreat_".length()).split("\\|", 2);
        if (payload.length != 2) return;
        Tile source = game.getTileByPosition(payload[0]);
        Tile destination = game.getTileByPosition(payload[1]);
        List<String> ships = getSelection(game, SELECTED_SHIPS, player);
        if (source == null || destination == null || ships.isEmpty()) return;

        List<String> units = new ArrayList<>(ships);
        units.addAll(getSelection(game, SELECTED_CARGO, player));
        Map<String, Integer> unitCounts = new HashMap<>();
        for (String unit : units) unitCounts.merge(unit, 1, Integer::sum);
        String unitList = unitCounts.entrySet().stream()
                .map(entry -> entry.getValue() + " " + entry.getKey())
                .reduce((left, right) -> left + ", " + right)
                .orElse("");
        MoveUnitService.moveUnits(event, source, game, player.getColor(), unitList, destination, "space");

        game.removeStoredValue(SELECTED_SHIPS + player.getFaction());
        game.removeStoredValue(SELECTED_CARGO + player.getFaction());
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentation() + " resolved _Tactical Retreat_, moving " + unitList + " to "
                        + destination.getRepresentationForButtons(game, player) + ".");
        ButtonHelper.deleteMessage(event);
    }

    private static void sendShipButtons(ButtonInteractionEvent event, Game game, Player player, Tile source) {
        List<String> selected = getSelection(game, SELECTED_SHIPS, player);
        List<Button> buttons = new ArrayList<>();
        for (UnitKey unitKey : getEligibleShips(player, source)) {
            if (selected.stream().filter(unitKey.asyncID()::equals).count()
                            < source.getSpaceUnitHolder().getUnitCount(unitKey)
                    && selected.size() < 2) {
                UnitModel unit = player.getUnitFromUnitKey(unitKey);
                buttons.add(Buttons.green(
                        player.factionButtonChecker() + "tacticalRetreatShip_" + source.getPosition() + "|"
                                + unitKey.asyncID(),
                        "Select " + unit.getName(),
                        unitKey.unitEmoji()));
            }
        }
        if (!selected.isEmpty()) {
            buttons.add(Buttons.blue(
                    player.factionButtonChecker() + "tacticalRetreatDestination_" + source.getPosition(),
                    "Choose Destination"));
        }
        buttons.add(Buttons.red(player.factionButtonChecker() + "cancelTacticalRetreat", "Decline"));
        MessageHelper.editMessageWithButtons(
                event,
                player.getRepresentation() + ", select up to 2 non-fighter ships to move with _Tactical Retreat_."
                        + "\n-# Selected: " + (selected.isEmpty() ? "none" : String.join(", ", selected)),
                buttons);
    }

    private static void sendCargoButtons(
            ButtonInteractionEvent event, Game game, Player player, Tile source, Tile destination) {
        List<String> selected = getSelection(game, SELECTED_CARGO, player);
        List<Button> buttons = new ArrayList<>();
        for (UnitKey unitKey : source.getSpaceUnitHolder().getUnitKeysForPlayer(player)) {
            if (isCargo(player, source, unitKey)
                    && selected.stream().filter(unitKey.asyncID()::equals).count()
                            < source.getSpaceUnitHolder().getUnitCount(unitKey)) {
                UnitModel unit = player.getUnitFromUnitKey(unitKey);
                buttons.add(Buttons.green(
                        player.factionButtonChecker() + "tacticalRetreatCargo_" + source.getPosition() + "|"
                                + destination.getPosition() + "|" + unitKey.asyncID(),
                        "Transport " + unit.getName(),
                        unitKey.unitEmoji()));
            }
        }
        buttons.add(Buttons.red(
                player.factionButtonChecker() + "finishTacticalRetreat_" + source.getPosition() + "|"
                        + destination.getPosition(),
                "Finish Tactical Retreat"));
        MessageHelper.editMessageWithButtons(
                event,
                player.getRepresentation() + ", optionally select units in space to transport to "
                        + destination.getRepresentationForButtons(game, player) + ".\n-# Capacity is player-enforced.",
                buttons);
    }

    private static Set<UnitKey> getEligibleShips(Player player, Tile tile) {
        if (tile == null) return Set.of();
        return tile.getSpaceUnitHolder().getUnitKeysForPlayer(player).stream()
                .filter(unitKey -> {
                    UnitModel unit = player.getUnitFromUnitKey(unitKey);
                    return unit != null && unit.getIsShip() && unitKey.unitType() != UnitType.Fighter;
                })
                .collect(java.util.stream.Collectors.toSet());
    }

    private static boolean isCargo(Player player, Tile tile, UnitKey unitKey) {
        UnitModel unit = player.getUnitFromUnitKey(unitKey);
        return unit != null && (unitKey.unitType() == UnitType.Fighter || unit.getIsGroundForce());
    }

    private static UnitKey getUnitKey(Player player, Tile tile, String asyncID) {
        if (tile == null) return null;
        return tile.getSpaceUnitHolder().getUnitKeysForPlayer(player).stream()
                .filter(unitKey -> asyncID.equals(unitKey.asyncID()))
                .findFirst()
                .orElse(null);
    }

    private static List<String> getSelection(Game game, String key, Player player) {
        String value = game.getStoredValue(key + player.getFaction());
        return value.isBlank() ? new ArrayList<>() : new ArrayList<>(List.of(value.split(",")));
    }

    private static void storeSelection(Game game, String key, Player player, List<String> selection) {
        game.setStoredValue(key + player.getFaction(), String.join(",", selection));
    }

    private static List<Button> appendCancelButton(Player player, List<Button> buttons) {
        buttons.add(Buttons.red(player.factionButtonChecker() + "cancelTacticalRetreat", "Decline"));
        return buttons;
    }
}
