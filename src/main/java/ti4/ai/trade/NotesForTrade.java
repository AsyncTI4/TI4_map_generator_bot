package ti4.ai.trade;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.ai.brain.StrategyCard;
import ti4.ai.promissory.NoteGiving;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
class NotesForTrade {

    static final double OTHER_NOTE = 0.3;
    static final double UNKNOWN_NOTE_COST = 1.0;
    static final double INFINITE = Double.POSITIVE_INFINITY;
    private static final String TRADE_AGREEMENT = "_ta";
    private static final String CEASEFIRE = "_cf";
    private static final String POLITICAL_SECRET = "_ps";
    private static final double THRONE_SHARE = 0.8;
    private static final double OWN_THRONE_BACK = 1.0;
    private static final double THRONE_GIVE_COST = 6.0;
    private static final int GUARDED_GIVE_VP_GAP = 3;
    private static final double AGREEMENT_SHARE = 0.6;
    private static final double OWN_AGREEMENT_SHARE = 0.5;
    private static final double AGREEMENT_TRADE_BONUS = 1.0;
    private static final double CEASEFIRE_FROM_NEIGHBOUR = 2.0;
    private static final double CEASEFIRE_FROM_AFAR = 0.5;
    private static final double OWN_CEASEFIRE_BACK = 1.0;
    private static final double CEASEFIRE_TO_NEIGHBOUR = 2.0;
    private static final double CEASEFIRE_TO_AFAR = 1.0;
    private static final double POLITICAL_SECRET_VALUE = 0.5;
    private static final double POLITICAL_SECRET_WITH_AGENDAS = 1.5;
    private static final double OWN_POLITICAL_SECRET_COST = 1.0;
    private static final double ALLIANCE_VALUE = 0.3;
    private static final double ALLIANCE_GIVE_COST = 1.5;
    private static final double OWN_FACTION_NOTE_COST = 1.5;
    private static final double RETURN_DISCOUNT = 0.3;
    private static final Map<String, Double> NOTES_IT_PLAYS = Map.of(
            "ra", 1.5,
            "ms", 1.0,
            "gift", 1.0,
            "tekklar", 1.0,
            "antivirus", 0.5,
            "greyfire", 0.5,
            "favor", 0.5);

    static Optional<String> aliasOf(Player sender, String detail) {
        if (!StringUtils.isNumeric(detail)) return Optional.of(detail.replace(DealItem.ALIAS_UNDERSCORE, "_"));
        int id = Integer.parseInt(detail);
        return sender.getPromissoryNotes().entrySet().stream()
                .filter(entry -> entry.getValue() != null && entry.getValue() == id)
                .map(Map.Entry::getKey)
                .findFirst();
    }

    static double receiveValue(Game game, Player seat, String alias) {
        boolean own = Standings.same(game.getPNOwner(alias), seat);
        if (alias.endsWith(NoteGiving.THRONE)) return own ? ownThroneBack(game, seat, alias) : throneValue(game, seat);
        if (alias.endsWith(TRADE_AGREEMENT)) return agreementValue(game, seat, alias, own);
        if (alias.endsWith(CEASEFIRE)) return own ? OWN_CEASEFIRE_BACK : ceasefireValue(game, seat, alias);
        if (alias.endsWith(POLITICAL_SECRET)) {
            return game.isCustodiansScored() ? POLITICAL_SECRET_WITH_AGENDAS : POLITICAL_SECRET_VALUE;
        }
        if (alias.endsWith(NoteGiving.ALLIANCE)) return ALLIANCE_VALUE;
        return NOTES_IT_PLAYS.getOrDefault(alias, OTHER_NOTE);
    }

    static double giveCost(Game game, Player seat, Player receiver, String alias, boolean desperate) {
        Player owner = game.getPNOwner(alias);
        if (!Standings.same(owner, seat)) {
            double value = receiveValue(game, seat, alias);
            return Standings.same(owner, receiver) ? Math.max(0, value - RETURN_DISCOUNT) : value;
        }
        if (NoteGiving.isThroneOrAlliance(alias)) {
            if (!guardedGiveAllowed(game, seat, receiver, desperate)) return INFINITE;
            return alias.endsWith(NoteGiving.THRONE) ? THRONE_GIVE_COST : ALLIANCE_GIVE_COST;
        }
        if (alias.endsWith(TRADE_AGREEMENT)) return ownAgreementCost(game, seat);
        if (alias.endsWith(CEASEFIRE)) {
            return TradeLegality.neighbours(game, seat, receiver) ? CEASEFIRE_TO_NEIGHBOUR : CEASEFIRE_TO_AFAR;
        }
        if (alias.endsWith(POLITICAL_SECRET)) return OWN_POLITICAL_SECRET_COST;
        return OWN_FACTION_NOTE_COST;
    }

    static Optional<String> leastHarmful(Game game, Player seat, Player receiver, boolean desperate) {
        List<String> inPlayArea = seat.getPromissoryNotesInPlayArea();
        return seat.getPromissoryNotes().keySet().stream()
                .filter(alias -> !inPlayArea.contains(alias))
                .filter(alias -> Double.isFinite(giveCost(game, seat, receiver, alias, desperate)))
                .min(Comparator.comparingDouble((String alias) -> giveCost(game, seat, receiver, alias, desperate))
                        .thenComparingInt(alias -> NoteGiving.giveRank(game, alias, receiver.getFaction()))
                        .thenComparing(Comparator.naturalOrder()));
    }

    static boolean inHand(Player seat, String alias) {
        return seat.getPromissoryNotes().containsKey(alias)
                && !seat.getPromissoryNotesInPlayArea().contains(alias);
    }

    private static boolean guardedGiveAllowed(Game game, Player seat, Player receiver, boolean desperate) {
        return desperate
                && Standings.vp(receiver) <= Standings.vp(seat) - GUARDED_GIVE_VP_GAP
                && !Stinginess.nearWin(game, receiver);
    }

    private static double throneValue(Game game, Player seat) {
        return THRONE_SHARE * Desperation.vpWorth(game, seat);
    }

    private static double ownThroneBack(Game game, Player seat, String alias) {
        return Standings.others(game, seat).stream()
                .filter(holder -> holder.getPromissoryNotesInPlayArea().contains(alias))
                .findFirst()
                .map(holder ->
                        OWN_THRONE_BACK + Stinginess.rivalry(game, seat, holder) * Desperation.vpWorth(game, holder))
                .orElse(OWN_THRONE_BACK);
    }

    private static double agreementValue(Game game, Player seat, String alias, boolean own) {
        if (own) return OWN_AGREEMENT_SHARE * seat.getCommoditiesTotal();
        Player owner = game.getPNOwner(alias);
        return owner == null ? OTHER_NOTE : AGREEMENT_SHARE * owner.getCommoditiesTotal();
    }

    private static double ownAgreementCost(Game game, Player seat) {
        boolean replenishSoon = tradeUnplayed(game) || holdsTrade(game, seat);
        return OWN_AGREEMENT_SHARE * seat.getCommoditiesTotal() + (replenishSoon ? AGREEMENT_TRADE_BONUS : 0);
    }

    private static double ceasefireValue(Game game, Player seat, String alias) {
        Player owner = game.getPNOwner(alias);
        boolean near = owner != null && TradeLegality.neighbours(game, owner, seat);
        return near ? CEASEFIRE_FROM_NEIGHBOUR : CEASEFIRE_FROM_AFAR;
    }

    private static boolean tradeUnplayed(Game game) {
        return game.getPlayedSCs().stream().noneMatch(card -> StrategyCard.of(game, card) == StrategyCard.TRADE);
    }

    private static boolean holdsTrade(Game game, Player seat) {
        return seat.getSCs().stream().anyMatch(card -> StrategyCard.of(game, card) == StrategyCard.TRADE);
    }
}
