package ti4.discord.interactions.buttons.handlers.faction.base.yin;

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
import ti4.helpers.ButtonHelperModifyUnits;
import ti4.helpers.FoWHelper;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.message.MessageHelper;
import ti4.service.emoji.FactionEmojis;
import ti4.service.unit.DestroyUnitService;
import ti4.service.unit.ParsedUnit;

@UtilityClass
public class YinImpulseCoreButtonHandler {

    private static final String START_IMPULSE_CORE = "startImpulseCore_";
    private static final String RESOLVE_IMPULSE_CORE = "resolveImpulseCore_";
    private static final String BASE_IMPULSE_CORE = "ic";
    private static final String MILTYMOD_IMPULSE_CORE = "miltymod_ic";
    private static final List<UnitType> SACRIFICEABLE_SHIPS = List.of(UnitType.Cruiser, UnitType.Destroyer);

    public static Button getImpulseCoreButton(Player player, Tile tile) {
        if (!hasStartOfCombatImpulseCore(player)
                || getSacrificeableShips(player, tile).isEmpty()) return null;
        return Buttons.gray(
                player.factionButtonChecker() + START_IMPULSE_CORE + tile.getPosition(),
                "Use Impulse Core",
                FactionEmojis.Yin);
    }

    private static boolean hasStartOfCombatImpulseCore(Player player) {
        return player.hasTech(BASE_IMPULSE_CORE) || player.hasTech(MILTYMOD_IMPULSE_CORE);
    }

    private static List<UnitKey> getSacrificeableShips(Player player, Tile tile) {
        UnitHolder space = tile.getSpaceUnitHolder();
        if (space == null) return List.of();
        List<UnitKey> ships = new ArrayList<>();
        for (UnitType unitType : SACRIFICEABLE_SHIPS) {
            UnitKey unitKey = Units.getUnitKey(unitType, player.getColor());
            if (unitKey != null && space.getUnitCount(unitKey) > 0) ships.add(unitKey);
        }
        return ships;
    }

    @ButtonHandler(START_IMPULSE_CORE)
    public static void startImpulseCore(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        Tile tile = game.getTileByPosition(buttonID.substring(START_IMPULSE_CORE.length()));
        List<UnitKey> ships = tile == null ? List.of() : getSacrificeableShips(player, tile);
        if (ships.isEmpty()) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event, "You have no cruisers or destroyers in this system to destroy with _Impulse Core_.");
            return;
        }
        List<Button> buttons = new ArrayList<>();
        for (UnitKey ship : ships) {
            buttons.add(Buttons.red(
                    player.factionButtonChecker() + RESOLVE_IMPULSE_CORE + tile.getPosition() + "_"
                            + ship.unitTypeVal(),
                    "Destroy 1 " + ship.humanReadableName(),
                    ship.unitEmoji()));
        }
        buttons.add(Buttons.gray("deleteButtons", "Cancel"));
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentation() + ", please choose which of your ships to destroy with _Impulse Core_.",
                buttons);
    }

    @ButtonHandler(RESOLVE_IMPULSE_CORE)
    public static void resolveImpulseCore(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] parts = buttonID.substring(RESOLVE_IMPULSE_CORE.length()).split("_");
        Tile tile = game.getTileByPosition(parts[0]);
        UnitKey ship = Units.getUnitKey(parts[1], player.getColor());
        if (tile == null || ship == null || !getSacrificeableShips(player, tile).contains(ship)) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event, "That ship is no longer in this system to destroy with _Impulse Core_.");
            return;
        }
        ButtonHelper.deleteMessage(event);
        DestroyUnitService.destroyUnit(event, tile, game, new ParsedUnit(ship), true);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + " used _Impulse Core_ to destroy 1 of their "
                        + ship.unitEmoji() + " in " + tile.getRepresentationForButtons(game, player)
                        + ", producing 1 hit.");

        if (player.hasTech(MILTYMOD_IMPULSE_CORE)) {
            MessageHelper.sendMessageToChannelWithButtons(
                    event.getMessageChannel(),
                    player.getRepresentation() + ", please choose which opposing ship to hit.",
                    ButtonHelperModifyUnits.getOpposingUnitsToHit(player, game, tile, false));
            return;
        }

        Player opponent = findOpponent(game, player, tile);
        if (opponent == null) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), "No opponent found to assign the _Impulse Core_ hit.");
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                opponent.getRepresentationUnfogged()
                        + ", your opponent used _Impulse Core_ to produce 1 hit against your ships."
                        + " You must assign it to a non-fighter ship, if able.",
                ButtonHelper.getButtonsForRemovingAllUnitsInSystem(opponent, game, tile, "spacecombat"));
    }

    private static Player findOpponent(Game game, Player player, Tile tile) {
        for (Player p2 : game.getRealPlayersNNeutral()) {
            if (p2 != player
                    && FoWHelper.playerHasShipsInSystem(p2, tile)
                    && !player.getAllianceMembers().contains(p2.getFaction())) {
                return p2;
            }
        }
        return null;
    }
}
