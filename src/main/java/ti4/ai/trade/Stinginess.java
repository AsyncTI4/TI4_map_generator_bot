package ti4.ai.trade;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import ti4.ai.scoring.SpendUnlock;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelperAbilities;

@UtilityClass
class Stinginess {

    private static final double NEAR_WIN_RIVALRY = 1.0;
    private static final double FEEDS_LEADER = 0.6;
    private static final double PARTNER_COMMODITY = 0.5;
    private static final int NEAR_WIN_FROM_GOAL = 2;
    private static final int CONTENDER_FROM_GOAL = 3;
    private static final int HARMLESS_GAP = 2;
    private static final double TENTHS = 10.0;
    private static final int BASE_RIVALRY_TENTHS = 1;
    private static final int RIVALRY_TENTHS_PER_POINT = 1;
    private static final int LEADER_RIVALRY_TENTHS = 2;
    private static final int CONTENDER_RIVALRY_TENTHS = 3;
    private static final int MAX_RIVALRY_TENTHS = 9;
    private static final double PARTNER_DEBT = 0.8;
    private static final double PARTNER_NOTE = NotesForTrade.UNKNOWN_NOTE_COST;
    private static final double PARTNER_FRAGMENT = 1.0;
    private static final double MIRROR_COMPUTING_RATE = 2.0;
    private static final double VOTES_RATE = 1.25;
    private static final double MUNITIONS_RATE = 1.25;
    private static final double PLAIN_RATE = 1.0;
    private static final double DATAHUB_BONUS = 1.5;
    private static final int DATAHUB_TRADE_GOODS = 3;
    private static final double HACAN_COMMANDER_BONUS = 2.0;
    private static final int HACAN_COMMANDER_TRADE_GOODS = 10;
    private static final double PILLAGE_LEAK = 1.0;
    private static final String MIRROR_COMPUTING = "mc";
    private static final String HACAN_COMMANDER = "hacancommander";
    private static final String MUNITIONS = "munitions";
    private static final String DATAHUB = "qdn";
    private static final String PILLAGE = "pillage";
    private static final String OWN_PILLAGE_OPT_IN = "willPillageOwnTransactions";

    enum Veto {
        WIN("could let them win"),
        LEADER("feeds the leader"),
        UNSUPPORTED("unsupported items"),
        TWO_NOTES("two notes"),
        ILLEGAL("illegal");

        private final String reason;

        Veto(String reason) {
            this.reason = reason;
        }

        String reason() {
            return reason;
        }
    }

    static boolean nearWin(Game game, Player player) {
        int points = Standings.vp(player);
        return points >= Standings.goal(game) - NEAR_WIN_FROM_GOAL
                && Standings.players(game).stream().allMatch(other -> points >= Standings.vp(other));
    }

    static double rivalry(Game game, Player self, Player other) {
        if (nearWin(game, other)) return NEAR_WIN_RIVALRY;
        int gap = Standings.vp(other) - Standings.vp(self);
        if (gap <= -HARMLESS_GAP) return 0;
        int tenths = BASE_RIVALRY_TENTHS
                + RIVALRY_TENTHS_PER_POINT * Math.max(0, gap)
                + (strictLeader(game, other) ? LEADER_RIVALRY_TENTHS : 0)
                + (Standings.vp(other) >= Standings.goal(game) - CONTENDER_FROM_GOAL ? CONTENDER_RIVALRY_TENTHS : 0);
        return Math.min(MAX_RIVALRY_TENTHS, tenths) / TENTHS;
    }

    static double partnerGain(Game game, Player partner, Player seat, Deal deal) {
        return partnerGain(game, partner, seat, deal, false);
    }

    static double partnerGain(Game game, Player partner, Player seat, Deal deal, boolean seatPrivate) {
        String faction = partner.getFaction();
        double rate = tradeGoodRate(game, partner, seat);
        double gain = 0;
        for (DealItem item : deal.items()) {
            if (item.isTo(faction)) gain += valueToPartner(game, partner, seat, item, rate, seatPrivate);
            if (item.isFrom(faction)) gain -= costToPartner(item, rate);
        }
        int net = deal.netTradeGoodsTo(faction);
        int points = net > 0 ? Math.max(0, SpendUnlock.pointDelta(game, partner, net)) : 0;
        return gain
                + thresholdBonus(partner, net)
                + Desperation.phaseFactor(game) * Desperation.scoreValue(game, partner, points);
    }

    static Optional<String> veto(Game game, Player seat, Player partner, Deal deal) {
        return vetoes(game, seat, partner, deal).stream().findFirst().map(Veto::reason);
    }

    static List<Veto> vetoes(Game game, Player seat, Player partner, Deal deal) {
        List<Veto> vetoes = new ArrayList<>();
        int net = deal.netTradeGoodsTo(partner.getFaction());
        int points = net > 0 ? SpendUnlock.pointDelta(game, partner, net) : 0;
        if (points > 0 && Standings.vp(partner) + points >= Standings.goal(game)) vetoes.add(Veto.WIN);
        if (points > 0 && rivalry(game, seat, partner) >= FEEDS_LEADER) vetoes.add(Veto.LEADER);
        if (deal.hasUnsupported()) vetoes.add(Veto.UNSUPPORTED);
        if (deal.total(seat.getFaction(), ItemType.PROMISSORY) > 1
                || deal.total(partner.getFaction(), ItemType.PROMISSORY) > 1) {
            vetoes.add(Veto.TWO_NOTES);
        }
        if (!TradeLegality.isLegal(game, seat, partner, deal)) vetoes.add(Veto.ILLEGAL);
        return vetoes;
    }

    static double pillageLeak(Game game, Player seat, Player partner, Deal deal) {
        if (deal.isDebtOnly() || noPillager(game) || optedOut(game, seat) || optedOut(game, partner)) return 0;
        String faction = seat.getFaction();
        int after = seat.getTg()
                - deal.total(faction, ItemType.TRADE_GOODS)
                + deal.received(faction, ItemType.TRADE_GOODS)
                + deal.received(faction, ItemType.COMMODITIES);
        return ButtonHelperAbilities.canBePillaged(seat, game, after) ? PILLAGE_LEAK : 0;
    }

    static double tradeGoodRate(Game game, Player partner, Player seat) {
        if (partner.hasTech(MIRROR_COMPUTING)) return MIRROR_COMPUTING_RATE;
        boolean votes = game.playerHasLeaderUnlockedOrAlliance(partner, HACAN_COMMANDER) && game.isCustodiansScored();
        if (votes) return VOTES_RATE;
        if (partner.hasAbility(MUNITIONS) && TradeLegality.neighbours(game, partner, seat)) return MUNITIONS_RATE;
        return PLAIN_RATE;
    }

    private static boolean strictLeader(Game game, Player player) {
        int points = Standings.vp(player);
        return Standings.others(game, player).stream().allMatch(other -> points > Standings.vp(other));
    }

    private static double valueToPartner(
            Game game, Player partner, Player seat, DealItem item, double rate, boolean seatPrivate) {
        int amount = item.amount();
        return switch (item.type()) {
            case TRADE_GOODS, COMMODITIES -> rate * amount;
            case SEND_DEBT, CLEAR_DEBT -> PARTNER_DEBT * amount;
            case PROMISSORY -> noteFromSeat(game, partner, seat, item, seatPrivate);
            case FRAGMENTS -> PARTNER_FRAGMENT * amount;
            case UNSUPPORTED -> 0;
        };
    }

    private static double costToPartner(DealItem item, double rate) {
        int amount = item.amount();
        return switch (item.type()) {
            case TRADE_GOODS -> rate * amount;
            case COMMODITIES -> PARTNER_COMMODITY * amount;
            case SEND_DEBT, CLEAR_DEBT -> PARTNER_DEBT * amount;
            case PROMISSORY -> PARTNER_NOTE * amount;
            case FRAGMENTS -> PARTNER_FRAGMENT * amount;
            case UNSUPPORTED -> 0;
        };
    }

    private static double noteFromSeat(Game game, Player partner, Player seat, DealItem item, boolean seatPrivate) {
        if (item.isGenericNote() && seatPrivate) return PARTNER_NOTE * item.amount();
        Optional<String> alias = item.isGenericNote()
                ? NotesForTrade.leastHarmful(game, seat, partner, false)
                : NotesForTrade.aliasOf(seat, item.detail());
        if (alias.isEmpty()) return NotesForTrade.OTHER_NOTE;
        double cost = NotesForTrade.giveCost(game, seat, partner, alias.get(), true);
        return Double.isFinite(cost) ? cost : NotesForTrade.receiveValue(game, partner, alias.get());
    }

    private static double thresholdBonus(Player partner, int net) {
        int tradeGoods = partner.getTg();
        double bonus = 0;
        if (partner.hasTechReady(DATAHUB)
                && partner.getStrategicCC() >= 1
                && crosses(tradeGoods, net, DATAHUB_TRADE_GOODS)) {
            bonus += DATAHUB_BONUS;
        }
        if (lockedHacanCommander(partner) && crosses(tradeGoods, net, HACAN_COMMANDER_TRADE_GOODS)) {
            bonus += HACAN_COMMANDER_BONUS;
        }
        return bonus;
    }

    private static boolean crosses(int tradeGoods, int net, int threshold) {
        return tradeGoods < threshold && tradeGoods + net >= threshold;
    }

    private static boolean lockedHacanCommander(Player partner) {
        return partner.hasLeader(HACAN_COMMANDER) && !partner.hasLeaderUnlocked(HACAN_COMMANDER);
    }

    private static boolean noPillager(Game game) {
        return Standings.players(game).stream().noneMatch(player -> player.hasAbility(PILLAGE));
    }

    private static boolean optedOut(Game game, Player player) {
        return player.hasAbility(PILLAGE)
                && !game.isTwilightsFallMode()
                && !game.getStoredValue(OWN_PILLAGE_OPT_IN + player.getFaction())
                        .isEmpty();
    }
}
