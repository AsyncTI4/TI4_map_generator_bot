package ti4.discord.interactions.buttons.handlers.actioncards.bluereverie;

import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.message.MessageHelper;

@UtilityClass
public class EntenteBrActionCardHandler {
    private static final String RESOLVE = "resolveEntenteBr";
    private static final String SELECT_STRATEGY_CARD = "ententeStrategyCard_";
    private static final String SELECT_TARGET = "ententeTarget_";
    private static final String SELECT_RETURN = "ententeReturn_";

    public static void offerResolution(Game game, Player player, String message) {
        MessageHelper.sendMessageToChannelWithButton(
                player.getCorrectChannel(),
                message,
                Buttons.green(player.factionButtonChecker() + RESOLVE, "Resolve Entente"));
    }

    @ButtonHandler(RESOLVE)
    public static void resolveEntente(ButtonInteractionEvent event, Game game, Player player) {
        List<Button> buttons = getReadyStrategyCardButtons(game, player);
        if (buttons.isEmpty()) {
            ButtonHelper.deleteMessage(event);
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationNoPing() + " has no unexhausted strategy card for _Entente_.");
            return;
        }
        MessageHelper.editMessageWithButtons(
                event,
                player.getRepresentationNoPing() + ", choose an unexhausted strategy card to give for _Entente_.",
                buttons);
    }

    @ButtonHandler(SELECT_STRATEGY_CARD)
    public static void selectStrategyCard(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        int strategyCard = parseStrategyCard(buttonID.substring(SELECT_STRATEGY_CARD.length()));
        if (!isReadyStrategyCard(game, player, strategyCard)) {
            ButtonHelper.deleteTheOneButton(event);
            return;
        }
        List<Button> buttons = game.getRealPlayers().stream()
                .filter(target -> target != player
                        && !target.isEliminated()
                        && !target.getSCs().isEmpty())
                .map(target -> FoWHelper.fogSafeTargetButton(
                        player.factionButtonChecker() + SELECT_TARGET + strategyCard + "|" + target.getFaction(),
                        "gray",
                        target))
                .toList();
        if (buttons.isEmpty()) {
            ButtonHelper.deleteMessage(event);
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationNoPing() + " has no eligible player for _Entente_.");
            return;
        }
        MessageHelper.editMessageWithButtons(
                event,
                player.getRepresentationNoPing() + ", choose the player receiving "
                        + Helper.getSCName(strategyCard, game) + " for _Entente_.",
                buttons);
    }

    @ButtonHandler(SELECT_TARGET)
    public static void selectTarget(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] values = buttonID.substring(SELECT_TARGET.length()).split("\\|", 2);
        int strategyCard = values.length == 2 ? parseStrategyCard(values[0]) : -1;
        Player target = values.length == 2 ? game.getPlayerFromColorOrFaction(values[1]) : null;
        if (!isReadyStrategyCard(game, player, strategyCard)
                || target == null
                || target == player
                || target.isEliminated()
                || target.getSCs().isEmpty()) {
            ButtonHelper.deleteTheOneButton(event);
            return;
        }
        player.removeSC(strategyCard);
        target.addSC(strategyCard);
        List<Button> buttons = target.getSCs().stream()
                .map(sc -> Buttons.gray(
                        target.factionButtonChecker() + SELECT_RETURN + player.getFaction() + "|" + strategyCard + "|"
                                + sc,
                        Helper.getSCName(sc, game)))
                .toList();
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithButtons(
                target.getCorrectChannel(),
                target.getRepresentationNoPing() + " received " + Helper.getSCName(strategyCard, game) + " from "
                        + player.getRepresentationNoPing() + " for _Entente_. Choose the strategy card to give them.",
                buttons);
    }

    @ButtonHandler(SELECT_RETURN)
    public static void exchangeStrategyCards(ButtonInteractionEvent event, Game game, Player target, String buttonID) {
        String[] values = buttonID.substring(SELECT_RETURN.length()).split("\\|", 3);
        Player owner = values.length == 3 ? game.getPlayerFromColorOrFaction(values[0]) : null;
        int ownerStrategyCard = values.length == 3 ? parseStrategyCard(values[1]) : -1;
        int targetStrategyCard = values.length == 3 ? parseStrategyCard(values[2]) : -1;
        if (owner == null || owner == target || !target.getSCs().contains(targetStrategyCard)) {
            ButtonHelper.deleteTheOneButton(event);
            return;
        }
        target.removeSC(targetStrategyCard);
        owner.addSC(targetStrategyCard);
        ButtonHelper.deleteMessage(event);
        String message = owner.getRepresentationNoPing() + " gave " + Helper.getSCName(ownerStrategyCard, game) + " to "
                + target.getRepresentationNoPing() + " and received " + Helper.getSCName(targetStrategyCard, game)
                + " for _Entente_.";
        MessageHelper.sendMessageToChannel(owner.getCorrectChannel(), message);
        if (game.isFowMode()) {
            MessageHelper.sendMessageToChannel(target.getCorrectChannel(), message);
        }
    }

    private static List<Button> getReadyStrategyCardButtons(Game game, Player player) {
        return player.getSCs().stream()
                .filter(sc -> isReadyStrategyCard(game, player, sc))
                .map(sc -> Buttons.gray(
                        player.factionButtonChecker() + SELECT_STRATEGY_CARD + sc, Helper.getSCName(sc, game)))
                .toList();
    }

    private static boolean isReadyStrategyCard(Game game, Player player, int strategyCard) {
        return player.getSCs().contains(strategyCard)
                && !game.getPlayedSCs().contains(strategyCard)
                && game.getStoredValue("exhaustedSC" + strategyCard).isEmpty();
    }

    private static int parseStrategyCard(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
