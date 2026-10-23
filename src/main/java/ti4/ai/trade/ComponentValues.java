package ti4.ai.trade;

import java.util.List;
import lombok.experimental.UtilityClass;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
public class ComponentValues {

    private static final double TRADE_GOOD = 1.0;
    private static final double COMMODITY_RECEIVED = 1.0;
    private static final double OWN_COMMODITY_WITH_OUTLET = 0.6;
    private static final double OWN_COMMODITY_WITHOUT_OUTLET = 0.3;
    static final double DEBT_LIABILITY = 0.8;
    private static final double REACH_NOW = 0.9;
    private static final double REACH_IN_AGENDAS = 0.75;
    private static final double REACH_UNCERTAIN = 0.5;
    private static final double ENDGAME = 0.5;
    private static final double NOT_ENDGAME = 1.0;
    static final List<String> FRAGMENT_KINDS = FragmentValue.KINDS;

    static double receive(Game game, Player seat, Player from, DealItem item, double trustInFrom) {
        return receive(game, seat, from, item, trustInFrom, false);
    }

    static double receive(Game game, Player seat, Player from, DealItem item, double trustInFrom, boolean seatPrivate) {
        int amount = item.amount();
        return switch (item.type()) {
            case TRADE_GOODS -> TRADE_GOOD * amount;
            case COMMODITIES -> COMMODITY_RECEIVED * amount;
            case SEND_DEBT -> debtReceivable(game, seat, from, amount, trustInFrom);
            case CLEAR_DEBT -> DEBT_LIABILITY * amount;
            case FRAGMENTS -> fragmentsReceived(seat, item, seatPrivate);
            case PROMISSORY -> receivedNote(game, seat, from, item, seatPrivate);
            case ACTION_CARDS -> TradeActionCards.receiveValue(game, seat, from, item, seatPrivate);
            case PLANETS -> TradePlanets.receiveValue(game, item);
            case UNSUPPORTED -> 0;
        };
    }

    static double give(Game game, Player seat, Player to, DealItem item, double trustInTo) {
        return give(game, seat, to, item, trustInTo, false);
    }

    static double give(Game game, Player seat, Player to, DealItem item, double trustInTo, boolean seatPrivate) {
        int amount = item.amount();
        return switch (item.type()) {
            case TRADE_GOODS -> TRADE_GOOD * amount;
            case COMMODITIES -> ownCommodity(game, seat, to) * amount;
            case SEND_DEBT -> DEBT_LIABILITY * amount;
            case CLEAR_DEBT -> debtReceivable(game, seat, to, amount, trustInTo);
            case FRAGMENTS -> fragmentsGiven(seat, item, seatPrivate);
            case PROMISSORY -> givenNote(game, seat, to, item, seatPrivate);
            case ACTION_CARDS -> TradeActionCards.giveCost(game, seat, to, item, seatPrivate);
            case PLANETS -> NotesForTrade.INFINITE;
            case UNSUPPORTED -> 0;
        };
    }

    public static double ownCommodity(Game game, Player seat) {
        return ownCommodity(game, seat, null);
    }

    static double ownCommodity(Game game, Player seat, Player excluding) {
        return hasOutlet(game, seat, excluding) ? OWN_COMMODITY_WITH_OUTLET : OWN_COMMODITY_WITHOUT_OUTLET;
    }

    static boolean hasOutlet(Game game, Player seat, Player excluding) {
        return Standings.others(game, seat).stream()
                .filter(other -> !Standings.same(other, excluding))
                .filter(other -> other.getCommodities() + other.getTg() >= 1)
                .anyMatch(other -> TradeLegality.canTransactInActionPhase(game, seat, other));
    }

    static double debtReceivable(Game game, Player creditor, Player debtor, int count, double trust) {
        return count * trust * reach(game, creditor, debtor) * endgame(game);
    }

    private static double reach(Game game, Player creditor, Player debtor) {
        if (TradeLegality.canTransact(game, creditor, debtor) || TradeLegality.isAgendaPhase(game)) return REACH_NOW;
        return game.isCustodiansScored() ? REACH_IN_AGENDAS : REACH_UNCERTAIN;
    }

    private static double endgame(Game game) {
        return Desperation.contenderExists(game) ? ENDGAME : NOT_ENDGAME;
    }

    private static double receivedNote(Game game, Player seat, Player from, DealItem item, boolean seatPrivate) {
        if (item.isGenericNote()) return NotesForTrade.OTHER_NOTE * item.amount();
        return NotesForTrade.aliasOf(from, item.detail())
                .map(alias -> NotesForTrade.receiveValue(game, seat, alias, seatPrivate))
                .orElse(NotesForTrade.OTHER_NOTE);
    }

    private static double givenNote(Game game, Player seat, Player to, DealItem item, boolean seatPrivate) {
        if (item.isGenericNote()) {
            if (seatPrivate) return NotesForTrade.UNKNOWN_NOTE_COST * item.amount();
            return NotesForTrade.leastHarmful(game, seat, to)
                    .map(alias -> NotesForTrade.giveCost(game, seat, to, alias) * item.amount())
                    .orElse(NotesForTrade.INFINITE);
        }
        return NotesForTrade.aliasOf(seat, item.detail())
                .map(alias -> NotesForTrade.giveCost(game, seat, to, alias))
                .orElse(NotesForTrade.INFINITE);
    }

    static int fragmentsOfKind(Player player, String kind) {
        return FragmentValue.Holdings.of(player).of(kind);
    }

    public static double gainedFragment(Player seat, String kind) {
        return FragmentValue.gainedFragment(seat, kind);
    }

    private static double fragmentsReceived(Player seat, DealItem item, boolean seatPrivate) {
        FragmentValue.Holdings held = FragmentValue.Holdings.of(seat);
        double gain = FragmentValue.gain(
                held, item.fragmentKind(), item.amount(), !seatPrivate && FragmentValue.holdsHereticalWorks(seat));
        boolean completes = held.with(item.fragmentKind(), item.amount()).relics() > held.relics();
        return completes ? gain + FragmentValue.TRADE_TEMPO : gain;
    }

    private static double fragmentsGiven(Player seat, DealItem item, boolean seatPrivate) {
        return FragmentValue.loss(
                FragmentValue.Holdings.of(seat),
                item.fragmentKind(),
                item.amount(),
                !seatPrivate && FragmentValue.holdsHereticalWorks(seat));
    }
}
