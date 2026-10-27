package ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.natau;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
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
import ti4.helpers.CommandCounterHelper;
import ti4.helpers.FoWHelper;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.thundersedge.BreakthroughCommandHelper;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;
import ti4.service.RemoveCommandCounterService;

@UtilityClass
public class NatauBreakthroughHandler {
    private static final String TETHER = "nataubt";
    private static final String USE_TETHER = "natauUseTether";
    private static final String SELECT_ANOMALY = "natauTetherAnomaly_";
    private static final String SELECT_SHIP = "natauTetherShip_";

    public static void offerTetherOnActivation(Game game, Player activatingPlayer, Tile activeTile) {
        for (Player player : game.getRealPlayers()) {
            if (player == activatingPlayer
                    || !player.hasReadyBreakthrough(TETHER)
                    || !activeTile.isAnomaly(game, player)
                    || !CommandCounterHelper.hasCC(player, activeTile)
                    || !FoWHelper.playerHasUnitsInSystem(player, activeTile)) {
                continue;
            }
            MessageHelper.sendMessageToChannelWithButtons(
                    player.getCorrectChannel(),
                    player.getRepresentationNoPing() + ", you may exhaust _Tether_.",
                    List.of(Buttons.green(player.factionButtonChecker() + USE_TETHER, "Use Tether")));
        }
    }

    @ButtonHandler(USE_TETHER)
    public static void useTether(ButtonInteractionEvent event, Player player) {
        if (!player.hasReadyBreakthrough(TETHER)) {
            return;
        }
        BreakthroughCommandHelper.exhaustBreakthrough(player, TETHER);
        ButtonHelper.deleteMessage(event);
    }

    public static void offerTether(Game game, Player player) {
        Tile activeTile = game.getTileByPosition(game.getActiveSystem());
        List<Tile> sourceTiles = getEligibleSourceTiles(game, player);
        if (activeTile == null
                || !activeTile.isAnomaly(game, player)
                || !CommandCounterHelper.hasCC(player, activeTile)
                || !FoWHelper.playerHasUnitsInSystem(player, activeTile)) {
            return;
        }
        RemoveCommandCounterService.fromTile(player.getColor(), activeTile, game);
        if (sourceTiles.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationNoPing() + " removed their command token from "
                            + activeTile.getRepresentationForButtons(game, player)
                            + " with _Tether_. There are no eligible anomaly ships to move.");
            return;
        }
        List<Button> buttons = new ArrayList<>(sourceTiles.stream()
                .map(tile -> Buttons.green(
                        player.factionButtonChecker() + SELECT_ANOMALY + tile.getPosition(),
                        tile.getRepresentationForButtons(game, player),
                        tile.getTileEmoji(player)))
                .toList());
        buttons.add(Buttons.red("deleteButtons", "Done"));
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationNoPing()
                        + " removed their command token from "
                        + activeTile.getRepresentationForButtons(game, player)
                        + " with _Tether_. Please choose an anomaly from which to move a non-fighter ship into the active system.",
                buttons);
    }

    @ButtonHandler(SELECT_ANOMALY)
    public static void offerTetherShips(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        Tile sourceTile = game.getTileByPosition(buttonID.substring(SELECT_ANOMALY.length()));
        if (sourceTile == null) return;
        List<Button> buttons = sourceTile.getSpaceUnitHolder().getUnitKeysForPlayer(player).stream()
                .map(unitKey -> getNonFighterShip(player, unitKey))
                .filter(Objects::nonNull)
                .map(unit -> Buttons.green(
                        player.factionButtonChecker() + SELECT_SHIP + sourceTile.getPosition() + "|"
                                + unit.getAsyncId(),
                        "Move " + unit.getName(),
                        unit.getUnitEmoji()))
                .toList();
        if (buttons.isEmpty()) return;
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + ", choose a non-fighter ship to move into the active system.",
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(SELECT_SHIP)
    public static void resolveTetherShip(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload = buttonID.substring(SELECT_SHIP.length()).split("\\|", 2);
        if (payload.length != 2) return;
        Tile sourceTile = game.getTileByPosition(payload[0]);
        Tile activeTile = game.getTileByPosition(game.getActiveSystem());
        if (sourceTile == null || activeTile == null) return;
        UnitHolder sourceSpace = sourceTile.getSpaceUnitHolder();
        UnitKey unitKey = sourceSpace.getUnitKeysForPlayer(player).stream()
                .filter(key -> key.asyncID().equals(payload[1]))
                .findFirst()
                .orElse(null);
        UnitModel unit = unitKey == null ? null : getNonFighterShip(player, unitKey);
        if (unit == null) return;
        List<Integer> states = sourceSpace.removeUnit(unitKey, 1);
        activeTile.getSpaceUnitHolder().addUnitsWithStates(unitKey, states);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + " removed 1 " + unit.getName() + " from "
                        + sourceTile.getRepresentationForButtons(game, player) + " and placed it in "
                        + activeTile.getRepresentationForButtons(game, player) + " with _Tether_.");
        ButtonHelper.deleteMessage(event);
    }

    private static List<Tile> getEligibleSourceTiles(Game game, Player player) {
        return game.getTileMap().values().stream()
                .filter(tile -> !tile.getPosition().equals(game.getActiveSystem()))
                .filter(tile -> tile.isAnomaly(game, player))
                .filter(tile -> FoWHelper.playerHasShipsInSystem(player, tile))
                .filter(tile -> tile.getSpaceUnitHolder().getUnitKeysForPlayer(player).stream()
                        .anyMatch(unitKey -> getNonFighterShip(player, unitKey) != null))
                .toList();
    }

    private static UnitModel getNonFighterShip(Player player, UnitKey unitKey) {
        UnitModel unit = player.getUnitFromUnitKey(unitKey);
        return unit != null && unit.isNonFighterShip() ? unit : null;
    }
}
