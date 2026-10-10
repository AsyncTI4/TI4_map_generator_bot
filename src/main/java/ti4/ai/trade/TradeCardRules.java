package ti4.ai.trade;

import java.time.Duration;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.ai.AiSeats;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiMemory;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.brain.StrategyCard;
import ti4.ai.scoring.PaymentRules;
import ti4.ai.scoring.SpendUnlock;
import ti4.ai.tactical.TacticalRules;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
public class TradeCardRules {

    public enum FollowChoice {
        FREE,
        TOKEN,
        DECLINE
    }

    private record Settlement(Deal deal, Purpose purpose) {}

    private static final String TOKENS_KEY = "tradeTokens|";
    private static final String BASELINE_KEY = "tradeBaseline|";
    private static final String TERMS_KEY = "tradeTerms|";
    private static final String FOLLOW_KEY = "tradeFollow|";
    private static final String SETTLED_KEY = "tradeSettled|";
    private static final String LIQUIDITY_KEY = "tradeLiquidity|";
    private static final String HONOURED_KEY = "tradeHonoured|";
    private static final String FORCED_KEY = "tradeForced|";
    private static final String UNFOLLOWED_KEY = "tradeUnfollowed|";
    private static final String FOLLOW_PRESSED_KEY = "tradeFollowPressed|";
    private static final String HOLD_SINCE_KEY = "tradeHoldSince|";
    private static final String FREE = "free";
    private static final String TOKEN = "token";
    private static final String SENT = "sent";
    private static final String NOTHING = "nothing";
    private static final String COUNTERED = "countered";
    private static final String FOLLOWED = "followedSC";
    private static final String SEPARATOR = "_";
    private static final String KEY_SEPARATOR = "|";
    private static final String ENTRIES = ",";
    private static final String VALUE = ":";
    private static final String TRADE_AGREEMENT = "_ta";
    private static final long HONOUR_HOLD_MILLIS = Duration.ofMinutes(10).toMillis();
    private static final long LIQUIDITY_WAIT_MILLIS = Duration.ofMinutes(30).toMillis();
    private static final double AI_TRUST = 1.0;
    private static final double MIN_FOLLOW_VALUE = 0.5;
    private static final int TOKEN_FOLLOW_MIN_GAIN = 4;
    private static final int EXCLUDED_FEE = 2;
    private static final int HONOUR_SLACK = 2;
    private static final int MAX_FEE = 2;
    private static final Set<ItemType> GOODS = EnumSet.of(ItemType.TRADE_GOODS, ItemType.COMMODITIES);
    private static final Set<ItemType> FEES = EnumSet.of(ItemType.TRADE_GOODS, ItemType.SEND_DEBT);

    public static void recordPlay(AiTurnContext context) {
        Game game = context.game();
        String snapshot = Standings.others(game, context.seat()).stream()
                .map(player -> player.getFaction() + VALUE + player.getStrategicCC())
                .collect(Collectors.joining(ENTRIES));
        TradeMemory.rewrite(context.memory(), TOKENS_KEY + game.getRound(), snapshot);
        TradeMemory.rewrite(context.memory(), BASELINE_KEY + game.getRound(), "");
    }

    public static Optional<AiDecision> announce(AiTurnContext context) {
        Game game = context.game();
        Player holder = context.seat();
        Map<Player, Optional<Integer>> terms = new LinkedHashMap<>();
        Map<String, Optional<Integer>> byFaction = new LinkedHashMap<>();
        for (Player follower : Standings.others(game, holder)) {
            Optional<Integer> fee = TradeTerms.feeFor(game, holder, follower, Trust.of(context, follower));
            terms.put(follower, fee);
            byFaction.put(follower.getFaction(), fee);
        }
        TradeMemory.rewrite(context.memory(), TERMS_KEY + game.getRound(), TradeTerms.encode(byFaction));
        String text = TradeTerms.announcement(game, holder, terms, follower -> Trust.of(context, follower));
        return Optional.of(new AiDecision.Announce(text));
    }

    public static Optional<AiDecision> settle(AiTurnContext context) {
        Game game = context.game();
        Player holder = context.seat();
        OptionalInt card = playedTrade(game, holder);
        if (card.isEmpty() || !TradeLegality.isActionPhase(game) || OfferBuilder.active(context) || busy(context)) {
            return Optional.empty();
        }
        List<Player> followers = followers(game, card.getAsInt(), holder);
        Set<String> baseline = baseline(context, followers);
        for (Player follower : followers) {
            if (baseline.contains(follower.getFaction()) || waitsForAnswer(context, follower)) continue;
            Optional<AiDecision> decision = settleWith(context, follower);
            if (decision.isPresent()) return decision;
        }
        return Optional.empty();
    }

    public static FollowChoice followChoice(AiTurnContext context, Player holder) {
        Game game = context.game();
        Player seat = context.seat();
        int gain = seat.getCommoditiesTotal() - seat.getCommodities();
        if (TradeTerms.mastersOfTrade(seat)) return gain >= 1 ? FollowChoice.FREE : FollowChoice.DECLINE;
        if (holder == null || gain < 1 || !holdsOwnAgreement(seat)) return FollowChoice.DECLINE;
        if (AiSeats.isAiSeat(holder)) {
            return worthFollowingFree(game, seat, holder, gain) ? FollowChoice.FREE : FollowChoice.DECLINE;
        }
        boolean worthToken = gain >= TOKEN_FOLLOW_MIN_GAIN && ComponentValues.hasOutlet(game, seat, null);
        return worthToken ? FollowChoice.TOKEN : FollowChoice.DECLINE;
    }

    public static void followPressed(AiTurnContext context, Player holder, FollowChoice choice) {
        AiMemory memory = context.memory();
        int round = context.game().getRound();
        memory.put(FOLLOW_PRESSED_KEY + round, String.valueOf(context.now()));
        if (choice == FollowChoice.FREE && AiSeats.isAiSeat(holder)) {
            TradeMemory.rewrite(memory, HOLD_SINCE_KEY + round, String.valueOf(context.now()));
        }
    }

    public static int reservedCommodities(AiTurnContext context) {
        Optional<Player> holder = heldFor(context);
        return holder.isPresent() ? context.seat().getCommoditiesTotal() : 0;
    }

    static boolean holdsFor(AiTurnContext context, Player partner) {
        return heldFor(context)
                .filter(holder -> Standings.same(holder, partner))
                .isPresent();
    }

    static void observe(AiTurnContext context) {
        observeFollows(context);
        observeForcedRefresh(context);
    }

    static boolean honours(AiTurnContext context, Player offerer, Deal deal) {
        Game game = context.game();
        Player seat = context.seat();
        if (TradeTerms.mastersOfTrade(seat) || context.memory().has(honouredKey(game, offerer))) return false;
        OptionalInt card = playedTrade(game, offerer);
        if (card.isEmpty()) return false;
        boolean invited = AiSeats.isAiSeat(offerer) && followed(game, card.getAsInt(), seat);
        boolean forced = offerer.getFaction()
                .equals(context.memory().get(forcedKey(game)).orElse(""));
        return (invited || forced) && fitsTerms(seat, offerer, deal);
    }

    static void honoured(AiTurnContext context, Player holder) {
        context.memory().put(honouredKey(context.game(), holder), String.valueOf(context.now()));
    }

    static String settledKey(Game game, Player follower) {
        return SETTLED_KEY + game.getRound() + KEY_SEPARATOR + follower.getFaction();
    }

    static void countered(AiTurnContext context, Player follower) {
        context.memory().put(settledKey(context.game(), follower), COUNTERED);
    }

    static boolean followedFree(AiTurnContext context, Player follower) {
        return FREE.equals(
                context.memory().get(followKey(context.game(), follower)).orElse(""));
    }

    static Optional<Trust.Event> unpaidSettlement(AiTurnContext context, Player follower) {
        if (TradeTerms.mastersOfTrade(follower) || !followedFree(context, follower)) return Optional.empty();
        Optional<Integer> terms = storedTerms(context).getOrDefault(follower.getFaction(), Optional.of(0));
        return Optional.of(terms.isPresent() ? Trust.Event.FREE_FOLLOW_UNPAID : Trust.Event.EXCLUDED_REPLENISH_UNPAID);
    }

    private static boolean busy(AiTurnContext context) {
        return PaymentRules.isPending(context)
                || (context.isActivePlayer() && TacticalRules.inProgress(context.game(), context.seat()));
    }

    private static boolean waitsForAnswer(AiTurnContext context, Player follower) {
        return context.memory().has(settledKey(context.game(), follower))
                || PendingOffers.with(context, follower)
                || IncomingOffer.liveFrom(context, follower);
    }

    private static Optional<AiDecision> settleWith(AiTurnContext context, Player follower) {
        Optional<Settlement> settlement = settlement(context, follower);
        if (settlement.isEmpty()) return Optional.empty();
        Optional<AiDecision> start = OfferBuilder.start(
                context, follower, settlement.get().deal(), settlement.get().purpose());
        start.ifPresent(ignored -> mark(context, follower, SENT));
        return start;
    }

    private static Optional<Settlement> settlement(AiTurnContext context, Player follower) {
        Game game = context.game();
        Player holder = context.seat();
        int commodities = follower.getCommodities();
        boolean legal = TradeLegality.canTransact(game, holder, follower);
        if (TOKEN.equals(followKind(context, follower)) || TradeTerms.mastersOfTrade(follower)) {
            return evenWash(context, follower, commodities, legal);
        }
        int fee = fee(context, follower);
        if (legal && commodities >= fee + 1) return payout(context, follower, commodities, fee);
        if (fee == 0) {
            mark(context, follower, NOTHING);
            return Optional.empty();
        }
        String theirs = follower.getFaction();
        String ours = holder.getFaction();
        boolean paysInTradeGoods = legal && follower.getTg() >= fee;
        ItemType type = paysInTradeGoods ? ItemType.TRADE_GOODS : ItemType.SEND_DEBT;
        return Optional.of(new Settlement(Deal.EMPTY.adjust(theirs, ours, type, fee), Purpose.SETTLEMENT_FEE));
    }

    private static Optional<Settlement> evenWash(
            AiTurnContext context, Player follower, int commodities, boolean legal) {
        int washed = Math.min(TradeBudget.freeCommodities(context), commodities);
        if (washed < 1 || !legal) {
            mark(context, follower, TOKEN);
            return Optional.empty();
        }
        String theirs = follower.getFaction();
        String ours = context.faction();
        Deal deal = Deal.EMPTY
                .adjust(theirs, ours, ItemType.COMMODITIES, washed)
                .adjust(ours, theirs, ItemType.COMMODITIES, washed);
        return Optional.of(new Settlement(deal, Purpose.EVEN_WASH));
    }

    private static Optional<Settlement> payout(AiTurnContext context, Player follower, int commodities, int fee) {
        Game game = context.game();
        int freeCommodities = TradeBudget.freeCommodities(context);
        int freeTradeGoods = TradeBudget.freeTradeGoods(game, context.seat());
        int requested = commodities;
        if (freeCommodities + freeTradeGoods < requested - fee) {
            if (waitsForLiquidity(context, follower)) return Optional.empty();
            requested = freeCommodities + freeTradeGoods + fee;
        }
        int paid = requested - fee;
        int inCommodities = Math.min(paid, freeCommodities);
        String theirs = follower.getFaction();
        String ours = context.faction();
        Deal deal = Deal.EMPTY
                .adjust(theirs, ours, ItemType.COMMODITIES, requested)
                .adjust(ours, theirs, ItemType.COMMODITIES, inCommodities)
                .adjust(ours, theirs, ItemType.TRADE_GOODS, paid - inCommodities);
        return Optional.of(new Settlement(deal, Purpose.SETTLEMENT));
    }

    private static boolean waitsForLiquidity(AiTurnContext context, Player follower) {
        if (PendingOffers.count(context, Purpose::settles) == 0) return false;
        String key = LIQUIDITY_KEY + context.game().getRound() + KEY_SEPARATOR + follower.getFaction();
        OptionalLong since = TradeMemory.time(context.memory(), key);
        if (since.isEmpty()) {
            context.memory().put(key, String.valueOf(context.now()));
            return true;
        }
        return context.now() - since.getAsLong() < LIQUIDITY_WAIT_MILLIS;
    }

    private static int fee(AiTurnContext context, Player follower) {
        Optional<Integer> stored = storedTerms(context).get(follower.getFaction());
        if (stored != null) return stored.orElse(EXCLUDED_FEE);
        return TradeTerms.feeFor(context.game(), context.seat(), follower, Trust.of(context, follower))
                .orElse(EXCLUDED_FEE);
    }

    private static Map<String, Optional<Integer>> storedTerms(AiTurnContext context) {
        return TradeTerms.decode(
                context.memory().get(TERMS_KEY + context.game().getRound()).orElse(""));
    }

    private static void mark(AiTurnContext context, Player follower, String outcome) {
        context.memory().put(settledKey(context.game(), follower), outcome);
    }

    private static boolean worthFollowingFree(Game game, Player seat, Player holder, int gain) {
        Optional<Integer> fee = TradeTerms.feeFor(game, holder, seat, AI_TRUST);
        if (fee.isEmpty()) return false;
        int stack = seat.getCommoditiesTotal();
        boolean legal = TradeLegality.canTransact(game, holder, seat);
        if (!legal && holder.getDebtTokenCount(seat.getColor()) + fee.get() > DebtRules.DEBT_CAP) return false;
        if (couldWinWith(game, holder, stack)) return false;
        double ownCommodity = ComponentValues.ownCommodity(game, seat, holder);
        if (legal) return stack - fee.get() - ownCommodity * seat.getCommodities() >= MIN_FOLLOW_VALUE;
        return ownCommodity * gain - ComponentValues.DEBT_LIABILITY * fee.get() >= 0;
    }

    private static boolean couldWinWith(Game game, Player player, int tradeGoods) {
        int points = SpendUnlock.pointDelta(game, player, tradeGoods);
        return points > 0 && Standings.vp(player) + points >= Standings.goal(game);
    }

    private static boolean holdsOwnAgreement(Player seat) {
        return seat.getPromissoryNotes().containsKey(seat.getColor() + TRADE_AGREEMENT);
    }

    private static Optional<Player> heldFor(AiTurnContext context) {
        Game game = context.game();
        AiMemory memory = context.memory();
        OptionalLong since = TradeMemory.time(memory, HOLD_SINCE_KEY + game.getRound());
        if (since.isEmpty() || context.now() - since.getAsLong() >= HONOUR_HOLD_MILLIS) return Optional.empty();
        return tradeHolder(game, context.seat())
                .filter(AiSeats::isAiSeat)
                .filter(holder -> !memory.has(honouredKey(game, holder)));
    }

    private static Optional<Player> tradeHolder(Game game, Player seat) {
        return Standings.others(game, seat).stream()
                .filter(other -> playedTrade(game, other).isPresent())
                .findFirst();
    }

    private static void observeFollows(AiTurnContext context) {
        Game game = context.game();
        Player holder = context.seat();
        OptionalInt card = playedTrade(game, holder);
        if (card.isEmpty()) return;
        Set<String> followed = followers(game, card.getAsInt(), holder).stream()
                .map(Player::getFaction)
                .collect(Collectors.toSet());
        Map<String, Integer> tokens = tokens(context);
        Map<String, Integer> refreshed = new LinkedHashMap<>(tokens);
        for (Player other : Standings.others(game, holder)) {
            String faction = other.getFaction();
            if (!followed.contains(faction)) {
                refreshed.put(faction, other.getStrategicCC());
                continue;
            }
            String key = followKey(game, other);
            if (context.memory().has(key)) continue;
            boolean paidToken = tokens.containsKey(faction) && other.getStrategicCC() < tokens.get(faction);
            context.memory().put(key, paidToken ? TOKEN : FREE);
        }
        if (!refreshed.equals(tokens)) {
            String snapshot = refreshed.entrySet().stream()
                    .map(entry -> entry.getKey() + VALUE + entry.getValue())
                    .collect(Collectors.joining(ENTRIES));
            TradeMemory.rewrite(context.memory(), TOKENS_KEY + game.getRound(), snapshot);
        }
    }

    private static Map<String, Integer> tokens(AiTurnContext context) {
        Map<String, Integer> tokens = new LinkedHashMap<>();
        String stored =
                context.memory().get(TOKENS_KEY + context.game().getRound()).orElse("");
        for (String entry : StringUtils.split(stored, ENTRIES)) {
            String faction = StringUtils.substringBefore(entry, VALUE);
            OptionalInt count = TradeMemory.parseInt(StringUtils.substringAfter(entry, VALUE));
            if (!faction.isEmpty() && count.isPresent()) tokens.put(faction, count.getAsInt());
        }
        return tokens;
    }

    private static void observeForcedRefresh(AiTurnContext context) {
        Game game = context.game();
        Player seat = context.seat();
        Optional<Player> holder = tradeHolder(game, seat);
        if (holder.isEmpty()) return;
        AiMemory memory = context.memory();
        int round = game.getRound();
        int card = playedTrade(game, holder.get()).orElseThrow();
        if (!followed(game, card, seat)) {
            if (!memory.has(UNFOLLOWED_KEY + round))
                memory.put(UNFOLLOWED_KEY + round, holder.get().getFaction());
            return;
        }
        if (memory.has(UNFOLLOWED_KEY + round)
                && !memory.has(FOLLOW_PRESSED_KEY + round)
                && !memory.has(forcedKey(game))) {
            memory.put(forcedKey(game), holder.get().getFaction());
        }
    }

    private static Set<String> baseline(AiTurnContext context, List<Player> followers) {
        String key = BASELINE_KEY + context.game().getRound();
        Optional<String> stored = context.memory().get(key);
        if (stored.isPresent()) return Set.of(StringUtils.split(stored.get(), ENTRIES));
        String current = followers.stream().map(Player::getFaction).collect(Collectors.joining(ENTRIES));
        TradeMemory.rewrite(context.memory(), key, current);
        return Set.of(StringUtils.split(current, ENTRIES));
    }

    private static List<Player> followers(Game game, int card, Player holder) {
        Set<String> factions = followerFactions(game, card);
        return Standings.others(game, holder).stream()
                .filter(player -> factions.contains(player.getFaction()))
                .filter(player -> DealItem.tradable(player.getFaction()))
                .sorted(Comparator.comparing(player -> !AiSeats.isAiSeat(player)))
                .toList();
    }

    private static Set<String> followerFactions(Game game, int card) {
        String stored = game.getStoredValue(FOLLOWED + card + SEPARATOR + game.getRound());
        return Arrays.stream(stored.split(SEPARATOR))
                .filter(faction -> !faction.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static String followKind(AiTurnContext context, Player follower) {
        return context.memory().get(followKey(context.game(), follower)).orElse(FREE);
    }

    private static OptionalInt playedTrade(Game game, Player holder) {
        return holder.getSCs().stream()
                .filter(card -> game.getPlayedSCs().contains(card))
                .filter(card -> StrategyCard.of(game, card) == StrategyCard.TRADE)
                .mapToInt(Integer::intValue)
                .findFirst();
    }

    private static boolean followed(Game game, int card, Player seat) {
        return followerFactions(game, card).contains(seat.getFaction());
    }

    private static boolean fitsTerms(Player seat, Player holder, Deal deal) {
        String ours = seat.getFaction();
        boolean holderGivesGoods = deal.items().stream()
                .filter(item -> item.isFrom(holder.getFaction()))
                .allMatch(item -> GOODS.contains(item.type()));
        List<ItemType> given = deal.items().stream()
                .filter(item -> item.isFrom(ours))
                .map(DealItem::type)
                .distinct()
                .toList();
        if (!holderGivesGoods || given.size() != 1) return false;
        ItemType type = given.getFirst();
        int amount = deal.total(ours, type);
        if (type == ItemType.COMMODITIES) {
            int received = deal.received(ours, ItemType.TRADE_GOODS) + deal.received(ours, ItemType.COMMODITIES);
            return amount <= seat.getCommodities() && received >= amount - HONOUR_SLACK;
        }
        return FEES.contains(type) && amount <= MAX_FEE;
    }

    private static String followKey(Game game, Player follower) {
        return FOLLOW_KEY + game.getRound() + KEY_SEPARATOR + follower.getFaction();
    }

    private static String honouredKey(Game game, Player holder) {
        return HONOURED_KEY + game.getRound() + KEY_SEPARATOR + holder.getFaction();
    }

    private static String forcedKey(Game game) {
        return FORCED_KEY + game.getRound();
    }
}
