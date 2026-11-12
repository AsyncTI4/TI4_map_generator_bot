package ti4.ai.trade;

import java.util.List;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.StringUtils;
import ti4.ai.deal.HumanAiDeal;
import ti4.ai.deal.HumanAiDealRunner;
import ti4.ai.promise.Promise;
import ti4.ai.promise.PromiseBook;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.message.MessageHelper;

@UtilityClass
class PromiseDealButtons {

    @ButtonHandler(PromiseDeals.ACCEPT_PREFIX)
    public static void accept(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        Optional<Promise> promise = offer(game, player, buttonID, PromiseDeals.ACCEPT_PREFIX);
        if (promise.isEmpty()) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "This offer is no longer open.");
            return;
        }
        Player promiser = promise.get().promiserIn(game).orElseThrow();
        Player promisee = promise.get().promiseeIn(game).orElseThrow();
        if (!TradeLegality.canTransact(game, promiser, promisee)) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event,
                    "The deal is off: " + promiser.getFactionEmojiOrColor() + " and "
                            + promisee.getFactionEmojiOrColor() + " cannot transact right now.");
            return;
        }
        if (promisee.getTg() < promise.get().price()) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event,
                    "The deal is off: " + promisee.getFactionEmojiOrColor() + " no longer has "
                            + promise.get().price() + " trade goods.");
            return;
        }
        ButtonHelper.deleteAllButtons(event);
        if (PromiseDeals.paysOnResolution(game, promise.get())) {
            PromiseBook.save(game, promise.get().accepted(game));
            MessageHelper.sendMessageToChannel(
                    event.getChannel(),
                    promiser.getFactionEmojiOrColor() + " promises to "
                            + PromiseDeals.describe(game, promise.get(), true) + ". "
                            + promisee.getFactionEmojiOrColor() + " pays it "
                            + promise.get().price()
                            + " trade goods after the agenda if it resolves that way.");
            return;
        }
        HumanAiDealRunner.run(event, game, payment(promise.get()));
        PromiseBook.save(game, promise.get().accepted(game));
        MessageHelper.sendMessageToChannel(
                event.getChannel(),
                promisee.getFactionEmojiOrColor() + " paid " + promiser.getFactionEmojiOrColor() + " "
                        + promise.get().price() + " trade goods. " + promiser.getFactionEmojiOrColor()
                        + " promises to " + PromiseDeals.describe(game, promise.get(), true) + ".");
    }

    @ButtonHandler(PromiseDeals.PAY_PREFIX)
    public static void pay(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (player == null || !buttonID.startsWith(PromiseDeals.PAY_PREFIX)) return;
        Optional<Promise> owed =
                PromiseDeals.owedBy(game, player, StringUtils.removeStart(buttonID, PromiseDeals.PAY_PREFIX));
        if (owed.isEmpty()) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "Nothing is owed on this deal any more.");
            return;
        }
        Player payee = owed.get().promiserIn(game).orElseThrow();
        if (player.getTg() < owed.get().price() || !TradeLegality.canTransact(game, player, payee)) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event,
                    "You cannot pay " + owed.get().price() + " trade goods to " + payee.getFactionEmojiOrColor()
                            + " right now.");
            return;
        }
        ButtonHelper.deleteAllButtons(event);
        HumanAiDealRunner.run(event, game, payment(owed.get()));
        PromiseBook.save(game, owed.get().with(Promise.Status.PAID));
        MessageHelper.sendMessageToChannel(
                event.getChannel(),
                player.getFactionEmojiOrColor() + " paid " + payee.getFactionEmojiOrColor() + " "
                        + owed.get().price() + " trade goods as agreed.");
    }

    @ButtonHandler(PromiseDeals.DECLINE_PREFIX)
    public static void decline(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        Optional<Promise> promise = offer(game, player, buttonID, PromiseDeals.DECLINE_PREFIX);
        ButtonHelper.deleteAllButtons(event);
        if (promise.isEmpty()) return;
        PromiseBook.save(game, promise.get().with(Promise.Status.DECLINED));
        MessageHelper.sendMessageToChannel(
                event.getChannel(), player.getFactionEmojiOrColor() + " turned the offer down.");
    }

    private static Optional<Promise> offer(Game game, Player player, String buttonID, String prefix) {
        if (player == null || !buttonID.startsWith(prefix)) return Optional.empty();
        return PromiseDeals.openFor(game, player, StringUtils.removeStart(buttonID, prefix));
    }

    private static HumanAiDeal payment(Promise promise) {
        String other = promise.proposer().equals(promise.promiser()) ? promise.promisee() : promise.promiser();
        return new HumanAiDeal(
                promise.id(),
                promise.proposer(),
                other,
                "A promise for " + promise.price() + " trade goods",
                promise.round(),
                promise.expiresAt(),
                HumanAiDeal.Status.PENDING,
                "",
                "",
                List.of(HumanAiDeal.Step.transfer(
                        promise.promisee(), promise.promiser(), HumanAiDeal.Item.TRADE_GOODS, promise.price())));
    }
}
