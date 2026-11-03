package ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.iron;

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
import ti4.helpers.Helper;
import ti4.helpers.NewStuffHelper;
import ti4.helpers.Units.UnitKey;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;

@UtilityClass
public class IronBreakthroughHandler {
    private static final String SELECT_SYSTEM = "ironHyperstaticSystem_";
    private static final String PRODUCTION_DISCOUNT = "ironHyperstaticDiscount_";

    public static void offerHyperstaticRelaySystems(Game game, Player player) {
        List<Button> buttons = getHyperstaticRelaySystemButtons(game, player);
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationNoPing()
                            + " has no system containing both their command token and a unit with PRODUCTION.");
            return;
        }
        NewStuffHelper.sendPaginated(
                player.getCorrectChannel(),
                hyperstaticRelaySystemMessage(player),
                buttons,
                List.of(),
                player.factionButtonChecker() + SELECT_SYSTEM);
    }

    @ButtonHandler(SELECT_SYSTEM)
    public static void selectHyperstaticRelaySystem(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String message = hyperstaticRelaySystemMessage(player);
        List<Button> systemButtons = getHyperstaticRelaySystemButtons(game, player);
        String prefix = player.factionButtonChecker() + SELECT_SYSTEM;
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event, event.getMessageChannel(), systemButtons, message, prefix, buttonID)) {
            return;
        }

        Tile tile = game.getTileByPosition(buttonID.substring(SELECT_SYSTEM.length()));
        if (tile == null) {
            ButtonHelper.deleteTheOneButton(event);
            return;
        }

        int discount = Math.min(3, getProductionUnitCount(player, tile));
        game.setStoredValue(PRODUCTION_DISCOUNT + player.getFaction(), Integer.toString(discount));
        List<Button> buttons = Helper.getPlaceUnitButtons(event, player, game, tile, "ironBt", "place");
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation()
                        + ", resolve PRODUCTION in "
                        + tile.getRepresentationForButtons(game, player)
                        + " with _Hyperstatic Relays_. The combined cost is reduced by "
                        + discount
                        + ".",
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    public static int applyHyperstaticRelayProductionDiscount(Game game, Player player, int cost) {
        String key = PRODUCTION_DISCOUNT + player.getFaction();
        String storedDiscount = game.getStoredValue(key);
        if (storedDiscount.isBlank()) {
            return cost;
        }
        game.removeStoredValue(key);
        try {
            return Math.max(0, cost - Integer.parseInt(storedDiscount));
        } catch (NumberFormatException _) {
            return cost;
        }
    }

    private static List<Button> getHyperstaticRelaySystemButtons(Game game, Player player) {
        return game.getTileMap().values().stream()
                .filter(tile -> tile.hasPlayerCC(player))
                .filter(tile -> getProductionUnitCount(player, tile) > 0)
                .map(tile -> Buttons.green(
                        player.factionButtonChecker() + SELECT_SYSTEM + tile.getPosition(),
                        tile.getRepresentationForButtons(game, player)))
                .toList();
    }

    private static int getProductionUnitCount(Player player, Tile tile) {
        int count = 0;
        for (UnitHolder holder : tile.getUnitHolders().values()) {
            for (UnitKey key : holder.getUnitKeysForPlayer(player)) {
                UnitModel unit = player.getPriorityUnitByAsyncID(key.asyncID(), holder);
                if (unit != null && (unit.getProductionValue() > 0 || "iron_mech".equals(unit.getId()))) {
                    count += holder.getUnitCount(key);
                }
            }
        }
        return count;
    }

    private static String hyperstaticRelaySystemMessage(Player player) {
        return player.getRepresentation()
                + ", choose a system containing your command token and units with PRODUCTION for _Hyperstatic Relays_.";
    }
}
