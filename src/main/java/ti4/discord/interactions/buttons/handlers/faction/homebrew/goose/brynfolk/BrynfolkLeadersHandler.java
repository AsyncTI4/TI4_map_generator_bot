package ti4.discord.interactions.buttons.handlers.faction.homebrew.goose.brynfolk;

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
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperStats;
import ti4.helpers.NewStuffHelper;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.message.MessageHelper;
import ti4.service.emoji.FactionEmojis;
import ti4.service.unit.DestroyUnitService;

@UtilityClass
public class BrynfolkLeadersHandler {
    private static final String BLOT_DEBT_POOL = "Blot";
    private static final String SELECT_TARGET_TILE = "brynfolkHeroTargetTile_";
    private static final String SELECT_TARGET_HOLDER = "brynfolkHeroTargetHolder_";
    private static final String DESTROY_TARGET_UNIT = "brynfolkHeroDestroyUnit_";
    private static final String GAIN_TRADE_GOODS = "brynfolkHeroGainTradeGoods";
    private static final String SEPARATOR = "|";

    public static void resolveHero(Game game, Player player) {
        Map<String, Integer> tokens = new HashMap<>(player.getDebtTokens(BLOT_DEBT_POOL));
        if (tokens.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationNoPing() + " had no control tokens on Blot to return.");
            return;
        }
        for (Map.Entry<String, Integer> entry : tokens.entrySet()) {
            int count = entry.getValue() == null ? 0 : entry.getValue();
            if (count > 0) {
                player.removeDebtTokens(entry.getKey(), count, BLOT_DEBT_POOL);
            }
        }
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationNoPing()
                        + " returned all control tokens from Blot to their owners' reinforcements.");
        for (Map.Entry<String, Integer> entry : tokens.entrySet()) {
            Player tokenOwner = game.getPlayerFromColorOrFaction(entry.getKey());
            int count = entry.getValue() == null ? 0 : entry.getValue();
            if (tokenOwner == null || count < 1) {
                continue;
            }
            for (int token = 0; token < count; token++) {
                if (tokenOwner == player) {
                    offerOwnTokenReward(game, player);
                } else {
                    offerTargetUnitSelection(game, player, tokenOwner);
                }
            }
        }
    }

    @ButtonHandler(SELECT_TARGET_TILE)
    public static void selectTargetTile(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload = buttonID.substring(SELECT_TARGET_TILE.length()).split("\\|", 2);
        Player target = payload.length == 2 ? game.getPlayerFromColorOrFaction(payload[0]) : null;
        if (target == null) {
            return;
        }
        List<Button> buttons = targetTileButtons(game, player, target);
        String message = targetTileMessage(player, target);
        String prefix = player.factionButtonChecker() + SELECT_TARGET_TILE + target.getFaction() + SEPARATOR;
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event, event.getMessageChannel(), buttons, message, prefix, buttonID)) {
            return;
        }
        Tile tile = game.getTileByPosition(payload[1]);
        if (tile == null || !hasEligibleUnit(tile, target)) {
            return;
        }
        List<Button> holderButtons = holderButtons(player, target, tile);
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + ", please choose the unit holder containing the unit to destroy.",
                holderButtons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(SELECT_TARGET_HOLDER)
    public static void selectTargetHolder(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload = buttonID.substring(SELECT_TARGET_HOLDER.length()).split("\\|", 3);
        Player target = payload.length == 3 ? game.getPlayerFromColorOrFaction(payload[0]) : null;
        Tile tile = payload.length == 3 ? game.getTileByPosition(payload[1]) : null;
        UnitHolder holder = tile == null || payload.length != 3
                ? null
                : tile.getUnitHolders().get(payload[2]);
        if (target == null || holder == null || !hasEligibleUnit(holder, target)) {
            return;
        }
        List<Button> unitButtons = holder.getUnitKeysForPlayer(target).stream()
                .filter(BrynfolkLeadersHandler::isEligibleUnit)
                .map(key -> Buttons.red(
                        player.factionButtonChecker()
                                + DESTROY_TARGET_UNIT
                                + target.getFaction()
                                + SEPARATOR
                                + tile.getPosition()
                                + SEPARATOR
                                + holder.getName()
                                + SEPARATOR
                                + key.asyncID(),
                        "Destroy " + key.humanReadableName(),
                        key.unitEmoji()))
                .toList();
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + ", please choose the unit to destroy with _Gald Offering_.",
                unitButtons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(DESTROY_TARGET_UNIT)
    public static void destroyTargetUnit(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload = buttonID.substring(DESTROY_TARGET_UNIT.length()).split("\\|", 4);
        Player target = payload.length == 4 ? game.getPlayerFromColorOrFaction(payload[0]) : null;
        Tile tile = payload.length == 4 ? game.getTileByPosition(payload[1]) : null;
        UnitHolder holder = tile == null || payload.length != 4
                ? null
                : tile.getUnitHolders().get(payload[2]);
        UnitKey unitKey = holder == null || target == null || payload.length != 4
                ? null
                : holder.getUnitKeysForPlayer(target).stream()
                        .filter(key -> payload[3].equals(key.asyncID()))
                        .findFirst()
                        .orElse(null);
        if (unitKey == null || !isEligibleUnit(unitKey)) {
            return;
        }
        DestroyUnitService.destroyUnit(event, tile, game, unitKey, 1, holder, false);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + " destroyed 1 " + unitKey.humanReadableName() + " belonging to "
                        + target.getRepresentationNoPing() + " with _Gald Offering_.");
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(GAIN_TRADE_GOODS)
    public static void gainTradeGoods(ButtonInteractionEvent event, Game game, Player player) {
        ButtonHelperStats.gainTGs(event, game, player, 2, false);
        ButtonHelper.deleteMessage(event);
    }

    private static void offerOwnTokenReward(Game game, Player player) {
        List<Button> buttons = new ArrayList<>(ButtonHelper.getGainCCButtons(player));
        buttons.add(Buttons.green(
                player.factionButtonChecker() + GAIN_TRADE_GOODS, "Gain 2 Trade Goods", FactionEmojis.brynfolk));
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationNoPing()
                        + ", please choose whether to gain 1 command token or 2 trade goods with _Gald Offering_.",
                buttons);
    }

    private static void offerTargetUnitSelection(Game game, Player player, Player target) {
        List<Button> buttons = targetTileButtons(game, player, target);
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationNoPing() + " found no eligible unit belonging to "
                            + target.getRepresentationNoPing() + " to destroy with _Gald Offering_.");
            return;
        }
        NewStuffHelper.sendPaginated(
                player.getCorrectChannel(),
                targetTileMessage(player, target),
                buttons,
                List.of(),
                player.factionButtonChecker() + SELECT_TARGET_TILE + target.getFaction() + SEPARATOR);
    }

    private static List<Button> targetTileButtons(Game game, Player player, Player target) {
        return game.getTileMap().values().stream()
                .filter(tile -> hasEligibleUnit(tile, target))
                .map(tile -> Buttons.red(
                        player.factionButtonChecker()
                                + SELECT_TARGET_TILE
                                + target.getFaction()
                                + SEPARATOR
                                + tile.getPosition(),
                        tile.getRepresentationForButtons(game, player)))
                .toList();
    }

    private static List<Button> holderButtons(Player player, Player target, Tile tile) {
        return tile.getUnitHolders().values().stream()
                .filter(holder -> hasEligibleUnit(holder, target))
                .map(holder -> Buttons.red(
                        player.factionButtonChecker()
                                + SELECT_TARGET_HOLDER
                                + target.getFaction()
                                + SEPARATOR
                                + tile.getPosition()
                                + SEPARATOR
                                + holder.getName(),
                        holder.getName()))
                .toList();
    }

    private static boolean hasEligibleUnit(Tile tile, Player target) {
        return tile.getUnitHolders().values().stream().anyMatch(holder -> hasEligibleUnit(holder, target));
    }

    private static boolean hasEligibleUnit(UnitHolder holder, Player target) {
        return holder.getUnitKeysForPlayer(target).stream().anyMatch(BrynfolkLeadersHandler::isEligibleUnit);
    }

    private static boolean isEligibleUnit(UnitKey unitKey) {
        return unitKey.unitType() != UnitType.Flagship && unitKey.unitType() != UnitType.Warsun;
    }

    private static String targetTileMessage(Player player, Player target) {
        return player.getRepresentationNoPing() + ", please choose a system containing an eligible unit belonging to "
                + target.getRepresentationNoPing() + " to destroy with _Gald Offering_.";
    }
}
