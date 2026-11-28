package ti4.ai.trade;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import ti4.ai.eval.ValueScale;
import ti4.ai.promise.PromisePayment;
import ti4.ai.promissory.NoteGiving;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
class PaymentValue {

    private static final double FULL_TRUST = 1.0;

    static double received(Game game, Player receiver, Player payer, PromisePayment payment, double trust) {
        double notes = payment.notes().stream()
                .mapToDouble(alias -> NotesForTrade.receiveValue(game, receiver, alias))
                .sum();
        return trust * (ValueScale.TRADE_GOOD * payment.tradeGoods() + notes)
                + ComponentValues.debtReceivable(game, receiver, payer, payment.debt(), trust);
    }

    static double receivedAsPayerSees(Game game, Player payer, Player receiver, PromisePayment payment) {
        double notes = payment.notes().stream()
                .mapToDouble(alias -> NotesForTrade.publicReceiveValue(game, receiver, alias))
                .sum();
        return ValueScale.TRADE_GOOD * payment.tradeGoods()
                + notes
                + ComponentValues.debtReceivable(game, receiver, payer, payment.debt(), FULL_TRUST);
    }

    static double cost(Game game, Player payer, Player receiver, PromisePayment payment) {
        double notes = payment.notes().stream()
                .mapToDouble(alias -> NotesForTrade.giveCost(game, payer, receiver, alias))
                .sum();
        return ValueScale.TRADE_GOOD * payment.tradeGoods() + ComponentValues.DEBT_LIABILITY * payment.debt() + notes;
    }

    static Optional<PromisePayment> cheapestCovering(
            Game game, Player payer, Player receiver, double needed, int spendableTradeGoods) {
        int tradeGoods = Math.max(0, Math.min(spendableTradeGoods, (int) Math.ceil(needed / ValueScale.TRADE_GOOD)));
        List<PromisePayment> options = new ArrayList<>();
        withDebt(game, payer, receiver, needed, tradeGoods, List.of()).ifPresent(options::add);
        for (String note : spareNotes(game, payer, receiver)) {
            withDebt(game, payer, receiver, needed, tradeGoods, List.of(note)).ifPresent(options::add);
        }
        return options.stream()
                .filter(payment -> !payment.onlyTradeGoods())
                .min(Comparator.comparingDouble((PromisePayment payment) -> cost(game, payer, receiver, payment))
                        .thenComparingInt(PromisePayment::debt));
    }

    private static Optional<PromisePayment> withDebt(
            Game game, Player payer, Player receiver, double needed, int tradeGoods, List<String> notes) {
        PromisePayment base = new PromisePayment(tradeGoods, 0, notes);
        double shortfall = needed - receivedAsPayerSees(game, payer, receiver, base);
        if (shortfall <= 0) return Optional.of(base);
        double perDebt = ComponentValues.debtReceivable(game, receiver, payer, 1, FULL_TRUST);
        if (perDebt <= 0) return Optional.empty();
        int debt = (int) Math.ceil(shortfall / perDebt);
        if (debt > DebtRules.room(payer, receiver)) return Optional.empty();
        return Optional.of(new PromisePayment(tradeGoods, debt, notes));
    }

    private static List<String> spareNotes(Game game, Player payer, Player receiver) {
        List<String> inPlayArea = payer.getPromissoryNotesInPlayArea();
        return payer.getPromissoryNotes().keySet().stream()
                .filter(alias -> !inPlayArea.contains(alias))
                .filter(alias -> !alias.endsWith(NoteGiving.THRONE))
                .filter(alias -> Double.isFinite(NotesForTrade.giveCost(game, payer, receiver, alias)))
                .sorted()
                .toList();
    }
}
