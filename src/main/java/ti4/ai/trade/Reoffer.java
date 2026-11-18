package ti4.ai.trade;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.promise.Promise;
import ti4.ai.promise.PromiseBook;
import ti4.ai.promise.PromisePayment;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
class Reoffer {

    static final int MAX_DECLINES = 1;
    private static final int STEP = 1;
    private static final int MIN_PRICE = 1;

    static boolean mayDeal(List<Promise> between) {
        long declined = between.stream().filter(Reoffer::declined).count();
        return declined <= MAX_DECLINES && between.stream().allMatch(Reoffer::declined);
    }

    static Optional<PromiseDeals.Proposal> improved(Game game, Player seat, PromiseDeals.Proposal proposal) {
        Optional<Promise> before = lastDeclined(game, seat, proposal);
        if (before.isEmpty()) return Optional.of(proposal);
        PromisePayment refused = PromisePayment.of(game, before.get());
        return proposal.promisee() == seat
                ? raised(game, seat, proposal, refused)
                : lowered(proposal, refused.tradeGoods());
    }

    static void freeDeclinedWindows(AiTurnContext context) {
        Game game = context.game();
        Player seat = context.seat();
        for (Promise promise : PromiseBook.proposedBy(game, seat)) {
            if (!declined(promise) || PromiseDeals.paysOnResolution(game, promise)) continue;
            Optional<Player> partner = partnerOf(game, seat, promise);
            partner.ifPresent(other -> TradeLegality.releaseWindow(context, other, promise.offeredAt()));
        }
    }

    private static Optional<PromiseDeals.Proposal> raised(
            Game game, Player payer, PromiseDeals.Proposal proposal, PromisePayment refused) {
        PromisePayment offered = proposal.payment();
        int shortfall = size(refused) + STEP - size(offered);
        if (shortfall <= 0) return Optional.of(proposal);
        double gain = proposal.gain() - shortfall;
        if (gain < TradeValue.PROPOSE_MIN) return Optional.empty();
        Player payee = proposal.promiser();
        int spareTradeGoods = PromiseDeals.spendableTradeGoods(game, payer) - offered.tradeGoods();
        if (spareTradeGoods >= shortfall) {
            int tradeGoods = offered.tradeGoods() + shortfall;
            PromisePayment payment = new PromisePayment(tradeGoods, offered.debt(), offered.notes());
            return Optional.of(with(proposal, tradeGoods, gain, payment));
        }
        int debtRoom = DebtRules.room(payer, payee) - offered.debt();
        if (debtRoom < shortfall) return Optional.empty();
        PromisePayment payment = new PromisePayment(offered.tradeGoods(), offered.debt() + shortfall, offered.notes());
        return Optional.of(with(proposal, offered.tradeGoods(), gain, payment));
    }

    private static Optional<PromiseDeals.Proposal> lowered(PromiseDeals.Proposal proposal, int refusedPrice) {
        int price = Math.min(proposal.price(), refusedPrice - STEP);
        if (price < MIN_PRICE) return Optional.empty();
        double gain = proposal.gain() - (proposal.price() - price);
        if (gain < TradeValue.PROPOSE_MIN) return Optional.empty();
        return Optional.of(with(proposal, price, gain, PromisePayment.tradeGoods(price)));
    }

    private static PromiseDeals.Proposal with(
            PromiseDeals.Proposal proposal, int price, double gain, PromisePayment payment) {
        return new PromiseDeals.Proposal(
                proposal.term(),
                proposal.promiser(),
                proposal.promisee(),
                proposal.target(),
                proposal.votes(),
                price,
                gain,
                payment);
    }

    private static Optional<Promise> lastDeclined(Game game, Player seat, PromiseDeals.Proposal proposal) {
        return PromiseBook.proposedBy(game, seat).stream()
                .filter(Reoffer::declined)
                .filter(promise -> promise.term() == proposal.term())
                .filter(promise -> promise.target().equalsIgnoreCase(proposal.target()))
                .filter(promise -> promise.isPromiser(proposal.promiser()) && promise.isPromisee(proposal.promisee()))
                .filter(promise -> !promise.aboutVotes() || ti4.ai.agenda.PromisedVotes.onThisAgenda(game, promise))
                .max(Comparator.comparingLong(Promise::offeredAt));
    }

    private static Optional<Player> partnerOf(Game game, Player seat, Promise promise) {
        Optional<Player> promiser = promise.promiserIn(game);
        Optional<Player> promisee = promise.promiseeIn(game);
        if (promiser.isEmpty() || promisee.isEmpty()) return Optional.empty();
        return Optional.of(promiser.get() == seat ? promisee.get() : promiser.get());
    }

    private static int size(PromisePayment payment) {
        return payment.tradeGoods() + payment.debt();
    }

    private static boolean declined(Promise promise) {
        return promise.status() == Promise.Status.DECLINED;
    }
}
