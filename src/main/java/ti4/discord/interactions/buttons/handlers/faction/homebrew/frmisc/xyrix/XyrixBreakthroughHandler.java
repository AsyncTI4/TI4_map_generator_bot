package ti4.discord.interactions.buttons.handlers.faction.homebrew.frmisc.xyrix;

import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ActionCardHelper;
import ti4.helpers.ButtonHelper;
import ti4.helpers.NewStuffHelper;
import ti4.message.MessageHelper;
import ti4.model.Mapper;
import ti4.service.emoji.FactionEmojis;

@UtilityClass
public class XyrixBreakthroughHandler {
    private static final String BREAKTHROUGH = "xyrixbt";
    private static final String USE_STRATEGIC = "xyrixVirtualVampirismStrategic_";
    private static final String TAKE_PURGED = "xyrixVirtualVampirismTakePurged_";
    private static final String PURGE_HAND = "xyrixVirtualVampirismPurgeHand_";
    private static final String SECONDARY_PENDING = "xyrixVirtualVampirismSecondary";
    private static final String STRATEGIC_PENDING = "xyrixVirtualVampirismStrategic";

    public static void resolveSecondaryOfAnotherPlayersStrategyCard(Game game, Player player, int strategyCard) {
        if (!player.hasUnlockedBreakthrough(BREAKTHROUGH)
                || !isAnotherPlayersStrategyCard(game, player, strategyCard)
                || hasPendingSecondary(player, game.getRound(), strategyCard)) {
            return;
        }
        ActionCardHelper.drawActionCards(player, 1);
        if (player.getActionCards().isEmpty()) {
            return;
        }
        player.addToStoredList(SECONDARY_PENDING, pendingSecondary(game.getRound(), strategyCard));
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCardsInfoThread(),
                player.getRepresentationNoPing() + ", purge 1 action card from your hand for _Virtual Vampirism_.",
                purgeHandButtons(player, strategyCard));
    }

    public static void offerStrategicAction(Game game, Player player, int strategyCard, boolean winnuHero) {
        if (winnuHero
                || !player.hasReadyBreakthrough(BREAKTHROUGH)
                || !player.getSCs().contains(strategyCard)
                || getPurgedActionCardButtons(player.getGame(), player).isEmpty()) {
            return;
        }
        MessageHelper.sendMessageToChannelWithButton(
                player.getCardsInfoThread(),
                player.getRepresentationNoPing()
                        + ", you may exhaust _Virtual Vampirism_ to add 1 purged action card to your hand.",
                Buttons.green(
                        player.factionButtonChecker() + USE_STRATEGIC + game.getRound() + "|" + strategyCard,
                        "Use Virtual Vampirism",
                        FactionEmojis.xyrix));
    }

    @ButtonHandler(USE_STRATEGIC)
    public static void useStrategicAction(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload = buttonID.substring(USE_STRATEGIC.length()).split("\\|", 2);
        Integer round = payload.length == 2 ? parseInteger(payload[0]) : null;
        Integer strategyCard = payload.length == 2 ? parseInteger(payload[1]) : null;
        List<Button> buttons = getPurgedActionCardButtons(game, player);
        if (round == null
                || strategyCard == null
                || round != game.getRound()
                || !player.hasReadyBreakthrough(BREAKTHROUGH)
                || !player.getSCs().contains(strategyCard)
                || buttons.isEmpty()) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        player.setBreakthroughExhausted(BREAKTHROUGH, true);
        player.setStoredValue(STRATEGIC_PENDING, Integer.toString(game.getRound()));
        NewStuffHelper.sendPaginated(
                player.getCardsInfoThread(),
                player.getRepresentationNoPing()
                        + ", choose 1 purged action card to add to your hand with _Virtual Vampirism_.",
                buttons,
                List.of(Buttons.DONE_DELETE_BUTTONS),
                takePurgedPrefix(player));
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(TAKE_PURGED)
    public static void takePurgedActionCard(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        List<Button> buttons = getPurgedActionCardButtons(game, player);
        if (!Integer.toString(game.getRound()).equals(player.getStoredValue(STRATEGIC_PENDING))) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event,
                player.getCardsInfoThread(),
                buttons,
                List.of(Buttons.DONE_DELETE_BUTTONS),
                player.getRepresentationNoPing()
                        + ", choose 1 purged action card to add to your hand with _Virtual Vampirism_.",
                takePurgedPrefix(player),
                buttonID)) {
            return;
        }
        Integer actionCardIndex = parseInteger(buttonID.substring(TAKE_PURGED.length()));
        if (actionCardIndex == null || !game.pickActionCardFromPurged(player.getUserID(), actionCardIndex)) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        player.removeStoredValue(STRATEGIC_PENDING);
        ActionCardHelper.sendActionCardInfo(game, player);
        ButtonHelper.checkACLimit(game, player);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationNoPing()
                        + " added 1 purged action card to their hand with _Virtual Vampirism_.");
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(PURGE_HAND)
    public static void purgeActionCardFromHand(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload = buttonID.substring(PURGE_HAND.length()).split("\\|", 2);
        Integer round = payload.length == 2 ? parseInteger(payload[0]) : null;
        String[] cardAndIndex = payload.length == 2 ? payload[1].split(":", 2) : new String[0];
        Integer strategyCard = cardAndIndex.length == 2 ? parseInteger(cardAndIndex[0]) : null;
        Integer actionCardIndex = cardAndIndex.length == 2 ? parseInteger(cardAndIndex[1]) : null;
        if (round == null
                || strategyCard == null
                || actionCardIndex == null
                || round != game.getRound()
                || !hasPendingSecondary(player, round, strategyCard)
                || !game.purgedActionCard(player.getUserID(), actionCardIndex)) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        player.removeFromStoredList(SECONDARY_PENDING, pendingSecondary(round, strategyCard));
        ActionCardHelper.sendActionCardInfo(game, player);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + " purged 1 action card with _Virtual Vampirism_.");
        ButtonHelper.deleteMessage(event);
    }

    public static void clearRoundState(Player player) {
        player.removeStoredValue(SECONDARY_PENDING);
        player.removeStoredValue(STRATEGIC_PENDING);
    }

    private static List<Button> purgeHandButtons(Player player, int strategyCard) {
        return player.getActionCards().entrySet().stream()
                .map(entry -> Buttons.red(
                        player.factionButtonChecker() + PURGE_HAND
                                + player.getGame().getRound() + "|" + strategyCard + ":" + entry.getValue(),
                        "Purge " + Mapper.getActionCard(entry.getKey()).getName()))
                .toList();
    }

    private static List<Button> getPurgedActionCardButtons(Game game, Player player) {
        return game.getPurgedActionCards().entrySet().stream()
                .map(entry -> Buttons.green(
                        takePurgedPrefix(player) + entry.getValue(),
                        Mapper.getActionCard(entry.getKey()).getName()))
                .toList();
    }

    private static String takePurgedPrefix(Player player) {
        return player.factionButtonChecker() + TAKE_PURGED;
    }

    private static boolean isAnotherPlayersStrategyCard(Game game, Player player, int strategyCard) {
        return game.getRealPlayers().stream()
                .anyMatch(holder -> holder != player && holder.getSCs().contains(strategyCard));
    }

    private static boolean hasPendingSecondary(Player player, int round, int strategyCard) {
        return player.getStoredList(SECONDARY_PENDING).contains(pendingSecondary(round, strategyCard));
    }

    private static String pendingSecondary(int round, int strategyCard) {
        return round + ":" + strategyCard;
    }

    private static Integer parseInteger(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
