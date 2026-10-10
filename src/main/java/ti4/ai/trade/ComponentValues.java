package ti4.ai.trade;

import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;
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
    private static final double FRAGMENT = 1.0;
    private static final double FRAGMENT_COMPLETING_SET = 2.0;
    private static final double FRAGMENT_FOR_HERETICAL_WORKS = 3.0;
    private static final int FRAGMENTS_PER_RELIC = 3;
    private static final int HERETICAL_WORKS_FRAGMENTS = 2;
    private static final String HERETICAL_WORKS = "dhw";
    private static final String CULTURAL = "CRF";
    private static final String INDUSTRIAL = "IRF";
    private static final String HAZARDOUS = "HRF";
    private static final String UNKNOWN = "URF";
    static final List<String> FRAGMENT_KINDS = List.of(CULTURAL, INDUSTRIAL, HAZARDOUS, UNKNOWN);

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
            case FRAGMENTS -> fragments(seat, item, seatPrivate);
            case PROMISSORY -> receivedNote(game, seat, from, item);
            case UNSUPPORTED -> 0;
        };
    }

    static double give(Game game, Player seat, Player to, DealItem item, double trustInTo, boolean desperate) {
        return give(game, seat, to, item, trustInTo, desperate, false);
    }

    static double give(
            Game game,
            Player seat,
            Player to,
            DealItem item,
            double trustInTo,
            boolean desperate,
            boolean seatPrivate) {
        int amount = item.amount();
        return switch (item.type()) {
            case TRADE_GOODS -> TRADE_GOOD * amount;
            case COMMODITIES -> ownCommodity(game, seat, to) * amount;
            case SEND_DEBT -> DEBT_LIABILITY * amount;
            case CLEAR_DEBT -> debtReceivable(game, seat, to, amount, trustInTo);
            case FRAGMENTS -> NotesForTrade.INFINITE;
            case PROMISSORY -> givenNote(game, seat, to, item, desperate, seatPrivate);
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

    private static double receivedNote(Game game, Player seat, Player from, DealItem item) {
        if (item.isGenericNote()) return NotesForTrade.OTHER_NOTE * item.amount();
        return NotesForTrade.aliasOf(from, item.detail())
                .map(alias -> NotesForTrade.receiveValue(game, seat, alias))
                .orElse(NotesForTrade.OTHER_NOTE);
    }

    private static double givenNote(
            Game game, Player seat, Player to, DealItem item, boolean desperate, boolean seatPrivate) {
        if (item.isGenericNote()) {
            if (seatPrivate) return NotesForTrade.UNKNOWN_NOTE_COST * item.amount();
            return NotesForTrade.leastHarmful(game, seat, to, false)
                    .map(alias -> NotesForTrade.giveCost(game, seat, to, alias, false) * item.amount())
                    .orElse(NotesForTrade.INFINITE);
        }
        return NotesForTrade.aliasOf(seat, item.detail())
                .map(alias -> NotesForTrade.giveCost(game, seat, to, alias, desperate))
                .orElse(NotesForTrade.INFINITE);
    }

    static int fragmentsOfKind(Player player, String kind) {
        return switch (kind) {
            case CULTURAL -> player.getCrf();
            case INDUSTRIAL -> player.getIrf();
            case HAZARDOUS -> player.getHrf();
            case UNKNOWN -> player.getUrf();
            default -> 0;
        };
    }

    public static double gainedFragment(Player seat, String kind) {
        int held = FRAGMENT_KINDS.stream()
                .mapToInt(known -> fragmentsOfKind(seat, known))
                .sum();
        boolean hereticalWorks =
                seat.getSecretsUnscored().containsKey(HERETICAL_WORKS) && held < HERETICAL_WORKS_FRAGMENTS;
        return fragmentValue(hereticalWorks, fragmentsToward(seat, kind));
    }

    private static double fragments(Player seat, DealItem item, boolean seatPrivate) {
        int towardSet = fragmentsToward(seat, item.fragmentKind());
        int held = FRAGMENT_KINDS.stream()
                .mapToInt(kind -> fragmentsOfKind(seat, kind))
                .sum();
        boolean hereticalWorks = !seatPrivate && seat.getSecretsUnscored().containsKey(HERETICAL_WORKS);
        return IntStream.range(0, item.amount())
                .mapToDouble(index ->
                        fragmentValue(hereticalWorks && held + index < HERETICAL_WORKS_FRAGMENTS, towardSet + index))
                .sum();
    }

    private static int fragmentsToward(Player seat, String kind) {
        int known = UNKNOWN.equals(kind) ? largestKnownKind(seat) : fragmentsOfKind(seat, kind);
        return known + fragmentsOfKind(seat, UNKNOWN);
    }

    private static int largestKnownKind(Player seat) {
        return Stream.of(CULTURAL, INDUSTRIAL, HAZARDOUS)
                .mapToInt(kind -> fragmentsOfKind(seat, kind))
                .max()
                .orElse(0);
    }

    private static double fragmentValue(boolean forHereticalWorks, int towardSet) {
        if (forHereticalWorks) return FRAGMENT_FOR_HERETICAL_WORKS;
        return towardSet == FRAGMENTS_PER_RELIC - 1 ? FRAGMENT_COMPLETING_SET : FRAGMENT;
    }
}
