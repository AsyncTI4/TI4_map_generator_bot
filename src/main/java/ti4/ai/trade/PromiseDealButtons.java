package ti4.ai.trade;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.StringUtils;
import ti4.ai.deal.HumanAiDeal;
import ti4.ai.deal.HumanAiDealRunner;
import ti4.ai.promise.Promise;
import ti4.ai.promise.PromiseBook;
import ti4.ai.promise.PromisePayment;
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
        PromisePayment payment = PromisePayment.of(game, promise.get());
        if (!payment.affordable(promisee)) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event,
                    "The deal is off: " + promisee.getFactionEmojiOrColor() + " no longer has " + payment.describe()
                            + ".");
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
                            + payment.describe()
                            + " after the agenda if it resolves that way.");
            return;
        }
        HumanAiDealRunner.run(event, game, payment(game, promise.get()));
        PromiseBook.save(game, promise.get().accepted(game));
        MessageHelper.sendMessageToChannel(
                event.getChannel(),
                promisee.getFactionEmojiOrColor() + " paid " + promiser.getFactionEmojiOrColor() + " "
                        + payment.describe() + ". " + promiser.getFactionEmojiOrColor()
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
        PromisePayment payment = PromisePayment.of(game, owed.get());
        if (!payment.affordable(player) || !TradeLegality.canTransact(game, player, payee)) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event,
                    "You cannot pay " + payment.describe() + " to " + payee.getFactionEmojiOrColor() + " right now.");
            return;
        }
        ButtonHelper.deleteAllButtons(event);
        HumanAiDealRunner.run(event, game, payment(game, owed.get()));
        PromiseBook.save(game, owed.get().with(Promise.Status.PAID));
        MessageHelper.sendMessageToChannel(
                event.getChannel(),
                player.getFactionEmojiOrColor() + " paid " + payee.getFactionEmojiOrColor() + " " + payment.describe()
                        + " as agreed.");
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

    private static HumanAiDeal payment(Game game, Promise promise) {
        String other = promise.proposer().equals(promise.promiser()) ? promise.promisee() : promise.promiser();
        PromisePayment payment = PromisePayment.of(game, promise);
        return new HumanAiDeal(
                promise.id(),
                promise.proposer(),
                other,
                "A promise for " + payment.describe(),
                promise.round(),
                promise.expiresAt(),
                HumanAiDeal.Status.PENDING,
                "",
                "",
                steps(promise, payment));
    }

    private static List<HumanAiDeal.Step> steps(Promise promise, PromisePayment payment) {
        List<HumanAiDeal.Step> steps = new ArrayList<>();
        String from = promise.promisee();
        String to = promise.promiser();
        if (payment.tradeGoods() > 0) {
            steps.add(HumanAiDeal.Step.transfer(from, to, HumanAiDeal.Item.TRADE_GOODS, payment.tradeGoods()));
        }
        if (payment.debt() > 0) steps.add(HumanAiDeal.Step.transfer(from, to, HumanAiDeal.Item.DEBT, payment.debt()));
        payment.notes().forEach(note -> steps.add(HumanAiDeal.Step.note(from, to, note)));
        return steps;
    }
}
