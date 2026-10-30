package ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.valhavn;

import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.FoWHelper;
import ti4.helpers.NewStuffHelper;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.helpers.bluereverie.BlueReverieTechHelper;
import ti4.helpers.thundersedge.BreakthroughCommandHelper;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;
import ti4.service.combat.CombatRollService;

@UtilityClass
public class ValhavnBreakthroughHandler {
    private static final String YGGDRASIL_CANNON = "valhavnbt";
    private static final String USE = "valhavnUseYggdrasilCannon_";
    private static final String DECLINE = "valhavnDeclineYggdrasilCannon_";
    private static final String SOURCE = "valhavnYggdrasilSource_";
    private static final String SHIP = "valhavnYggdrasilShip_";

    public static boolean offerYggdrasilCannon(
            GenericInteractionCreateEvent event, Game game, Player player, Player opponent, Tile activeTile, int hits) {
        if (!player.hasReadyBreakthrough(YGGDRASIL_CANNON)
                || hits < 1
                || !FoWHelper.playerHasShipsInSystem(player, activeTile)
                || sourceTiles(game, player, activeTile).isEmpty()) {
            return false;
        }
        String context = opponent.getFaction() + "|" + hits + "|" + activeTile.getPosition();
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentationNoPing()
                        + ", you may exhaust _Yggdrasil Cannon_ to cancel 1 SPACE CANNON OFFENSE hit and remove 1 ship from that unit's system to place it in the active system.",
                List.of(
                        Buttons.green(player.factionButtonChecker() + USE + context, "Use Yggdrasil Cannon"),
                        Buttons.red(player.factionButtonChecker() + DECLINE + context, "Decline")));
        return true;
    }

    @ButtonHandler(USE)
    @ButtonHandler(DECLINE)
    public static void resolveYggdrasilCannonChoice(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String prefix = buttonID.startsWith(USE) ? USE : DECLINE;
        Context context = getContext(game, buttonID.substring(prefix.length()));
        if (context == null) {
            return;
        }
        if (buttonID.startsWith(DECLINE)) {
            sendRemainingHitAssignment(game, player, context.opponent(), context.activeTile(), context.hits(), event);
            ButtonHelper.deleteMessage(event);
            return;
        }
        if (!player.hasReadyBreakthrough(YGGDRASIL_CANNON)) {
            return;
        }
        BreakthroughCommandHelper.exhaustBreakthrough(player, YGGDRASIL_CANNON);
        offerSourceTiles(event, game, player, context);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(SOURCE)
    public static void selectYggdrasilSource(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload = buttonID.substring(SOURCE.length()).split("\\|", 4);
        Context context = payload.length == 4
                ? getContext(game, String.join("|", List.of(payload[0], payload[1], payload[2])))
                : null;
        if (context == null) {
            return;
        }
        String sourcePosition = payload[3];
        List<Button> sourceButtons = sourceButtons(game, player, context);
        String message = sourceMessage(player);
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event,
                event.getMessageChannel(),
                sourceButtons,
                message,
                player.factionButtonChecker() + SOURCE + contextButtonContext(context) + "|",
                buttonID)) {
            return;
        }
        Tile sourceTile = game.getTileByPosition(sourcePosition);
        List<Button> shipButtons = sourceTile == null ? List.of() : shipButtons(player, context, sourceTile);
        if (shipButtons.isEmpty()) {
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentationNoPing()
                        + ", choose a ship to remove and place in the active system with _Yggdrasil Cannon_.",
                shipButtons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(SHIP)
    public static void moveYggdrasilShip(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload = buttonID.substring(SHIP.length()).split("\\|", 5);
        Context context = payload.length == 5
                ? getContext(game, String.join("|", List.of(payload[0], payload[1], payload[2])))
                : null;
        if (context == null) {
            return;
        }
        Tile sourceTile = game.getTileByPosition(payload[3]);
        UnitKey unitKey = sourceTile == null
                ? null
                : sourceTile.getSpaceUnitHolder().getUnitKeysForPlayer(player).stream()
                        .filter(key -> key.asyncID().equals(payload[4]))
                        .findFirst()
                        .orElse(null);
        UnitModel unit = unitKey == null ? null : player.getUnitFromUnitKey(unitKey);
        if (unit == null || !unit.getIsShip()) {
            return;
        }
        List<Integer> states = sourceTile.getSpaceUnitHolder().removeUnit(unitKey, 1);
        context.activeTile().getSpaceUnitHolder().addUnitsWithStates(unitKey, states);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + " cancelled 1 SPACE CANNON OFFENSE hit and removed 1 "
                        + unit.getName() + " from " + sourceTile.getRepresentationForButtons(game, player)
                        + " to place it in " + context.activeTile().getRepresentationForButtons(game, player)
                        + " with _Yggdrasil Cannon_.");
        sendRemainingHitAssignment(game, player, context.opponent(), context.activeTile(), context.hits() - 1, event);
        ButtonHelper.deleteMessage(event);
    }

    private static void offerSourceTiles(
            GenericInteractionCreateEvent event, Game game, Player player, Context context) {
        List<Button> buttons = sourceButtons(game, player, context);
        if (buttons.isEmpty()) {
            sendRemainingHitAssignment(
                    game, player, context.opponent(), context.activeTile(), context.hits() - 1, event);
            return;
        }
        NewStuffHelper.sendPaginated(
                event.getMessageChannel(),
                sourceMessage(player),
                buttons,
                List.of(),
                player.factionButtonChecker() + SOURCE + contextButtonContext(context) + "|");
    }

    private static List<Button> sourceButtons(Game game, Player player, Context context) {
        return sourceTiles(game, player, context.activeTile()).stream()
                .map(tile -> Buttons.green(
                        player.factionButtonChecker() + SOURCE + contextButtonContext(context) + "|"
                                + tile.getPosition(),
                        tile.getRepresentationForButtons(game, player)))
                .toList();
    }

    private static List<Button> shipButtons(Player player, Context context, Tile sourceTile) {
        return sourceTile.getSpaceUnitHolder().getUnitKeysForPlayer(player).stream()
                .map(key -> player.getUnitFromUnitKey(key) == null ? null : key)
                .filter(key -> key != null && player.getUnitFromUnitKey(key).getIsShip())
                .map(key -> Buttons.green(
                        player.factionButtonChecker() + SHIP + contextButtonContext(context) + "|"
                                + sourceTile.getPosition() + "|" + key.asyncID(),
                        "Place " + player.getUnitFromUnitKey(key).getName(),
                        player.getUnitFromUnitKey(key).getUnitEmoji()))
                .toList();
    }

    private static List<Tile> sourceTiles(Game game, Player player, Tile activeTile) {
        return game.getTileMap().values().stream()
                .filter(tile -> !tile.getPosition().equals(activeTile.getPosition()))
                .filter(tile -> hasPds(tile, player))
                .filter(tile -> tile.getSpaceUnitHolder().getUnitKeysForPlayer(player).stream()
                        .map(player::getUnitFromUnitKey)
                        .anyMatch(unit -> unit != null && unit.getIsShip()))
                .toList();
    }

    private static boolean hasPds(Tile tile, Player player) {
        return tile.getUnitHolders().values().stream()
                .anyMatch(holder -> holder.getUnitCount(UnitType.Pds, player) > 0);
    }

    private static void sendRemainingHitAssignment(
            Game game, Player player, Player opponent, Tile activeTile, int hits, GenericInteractionCreateEvent event) {
        if (hits < 1) {
            MessageHelper.sendMessageToChannel(
                    game.isFowMode() ? opponent.getCorrectChannel() : event.getMessageChannel(),
                    opponent.getRepresentationNoPing() + " has no remaining SPACE CANNON OFFENSE hits to assign.");
            return;
        }
        MessageChannel channel = game.isFowMode() ? opponent.getCorrectChannel() : event.getMessageChannel();
        if (!BlueReverieTechHelper.offerGravitonLaserSystemAfterSpaceCannon(
                game, player, opponent, activeTile, hits, event)) {
            CombatRollService.sendSpaceCannonOffenceHitAssignment(
                    game, player, opponent, activeTile, hits, channel, event);
        }
    }

    private static Context getContext(Game game, String rawContext) {
        String[] payload = rawContext.split("\\|", 3);
        if (payload.length != 3) {
            return null;
        }
        Player opponent = game.getPlayerFromColorOrFaction(payload[0]);
        Tile activeTile = game.getTileByPosition(payload[2]);
        try {
            int hits = Integer.parseInt(payload[1]);
            return opponent == null || activeTile == null || hits < 1 ? null : new Context(opponent, hits, activeTile);
        } catch (NumberFormatException _) {
            return null;
        }
    }

    private static String contextButtonContext(Context context) {
        return context.opponent().getFaction() + "|" + context.hits() + "|"
                + context.activeTile().getPosition();
    }

    private static String sourceMessage(Player player) {
        return player.getRepresentationNoPing()
                + ", choose the system containing the PDS that produced the hit to remove a ship from with _Yggdrasil Cannon_.";
    }

    private record Context(Player opponent, int hits, Tile activeTile) {}
}
