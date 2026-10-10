package ti4.ai.trade;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.ai.scoring.SpendUnlock;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
public class TradeTerms {

    public static final int MAX_ANNOUNCEMENT = 2000;
    private static final String MASTERS_OF_TRADE = "master_of_trade";
    private static final int HIGH_FEE = 2;
    private static final int LOW_FEE = 1;
    private static final int LARGE_STACK = 4;
    private static final double LEADS = 0.6;
    private static final double MAY_SCORE = 0.3;
    private static final String NEAR_WIN = "close to winning";
    private static final String LEADER = "leads";
    private static final String UNPAID = "unpaid debts";
    private static final String WOULD_SCORE = "it would let them score";
    private static final String NOT_OFFERED_FALLBACK = "no terms";
    private static final String ENTRIES = ",";
    private static final String VALUE = "=";
    private static final String NO_FEE = "-";
    private static final String LIST = ", ";
    private static final String TERMS = """
            Pressing **Replenish Commodities** accepts these terms: I then send you an offer, your commodities for \
            that many minus k trade goods, paid with my commodities first. If we can't transact right now, I ask for \
            k debt instead, payable when we can (or in the agenda phase). After **Replenish and Wash** you owe me k \
            trade goods. Following with a strategy token costs nothing more.""";

    public static Optional<Integer> feeFor(Game game, Player holder, Player follower, double trust) {
        if (mastersOfTrade(follower)
                || exclusionReason(game, holder, follower, trust).isPresent()) {
            return Optional.empty();
        }
        return Optional.of(fee(holder, follower, trust));
    }

    public static Optional<String> exclusionReason(Game game, Player holder, Player follower, double trust) {
        if (Stinginess.nearWin(game, follower)) return Optional.of(NEAR_WIN);
        double rivalry = Stinginess.rivalry(game, holder, follower);
        if (rivalry >= LEADS) return Optional.of(LEADER);
        if (trust < Trust.FREE_FOLLOW) return Optional.of(UNPAID);
        int payout = follower.getCommoditiesTotal() - fee(holder, follower, trust);
        if (rivalry >= MAY_SCORE && payout > 0 && SpendUnlock.pointDelta(game, follower, payout) > 0) {
            return Optional.of(WOULD_SCORE);
        }
        return Optional.empty();
    }

    public static boolean mastersOfTrade(Player follower) {
        return follower.hasAbility(MASTERS_OF_TRADE);
    }

    public static String announcement(
            Game game, Player holder, Map<Player, Optional<Integer>> terms, ToDoubleFunction<Player> trust) {
        String full = announcement(game, holder, terms, trust, Player::getRepresentationNoPing);
        if (full.length() <= MAX_ANNOUNCEMENT) return full;
        return StringUtils.abbreviate(announcement(game, holder, terms, trust, Player::getFaction), MAX_ANNOUNCEMENT);
    }

    public static String encode(Map<String, Optional<Integer>> terms) {
        return terms.entrySet().stream()
                .map(entry -> entry.getKey()
                        + VALUE
                        + entry.getValue().map(String::valueOf).orElse(NO_FEE))
                .collect(Collectors.joining(ENTRIES));
    }

    public static Map<String, Optional<Integer>> decode(String encoded) {
        Map<String, Optional<Integer>> terms = new LinkedHashMap<>();
        for (String entry : StringUtils.split(StringUtils.defaultString(encoded), ENTRIES)) {
            String faction = StringUtils.substringBefore(entry, VALUE);
            String fee = StringUtils.substringAfter(entry, VALUE);
            if (faction.isEmpty()) continue;
            if (StringUtils.isNumeric(fee)) terms.put(faction, Optional.of(Integer.parseInt(fee)));
            else if (NO_FEE.equals(fee)) terms.put(faction, Optional.empty());
        }
        return terms;
    }

    static int fee(Player holder, Player follower, double trust) {
        int stack = follower.getCommoditiesTotal();
        boolean high = stack >= LARGE_STACK || Standings.vp(follower) > Standings.vp(holder) || trust < Trust.LOWER_FEE;
        return Math.max(0, Math.min(high ? HIGH_FEE : LOW_FEE, stack - 1));
    }

    private static String announcement(
            Game game,
            Player holder,
            Map<Player, Optional<Integer>> terms,
            ToDoubleFunction<Player> trust,
            Function<Player, String> name) {
        List<String> free = new ArrayList<>();
        List<String> excluded = new ArrayList<>();
        List<String> traders = new ArrayList<>();
        terms.forEach((follower, fee) -> {
            if (mastersOfTrade(follower)) {
                traders.add(name.apply(follower));
            } else if (fee.isPresent()) {
                free.add(name.apply(follower) + " (" + feeLabel(fee.get()) + ")");
            } else {
                String reason = exclusionReason(game, holder, follower, trust.applyAsDouble(follower))
                        .orElse(NOT_OFFERED_FALLBACK);
                excluded.add(name.apply(follower) + " (" + reason + ")");
            }
        });
        StringBuilder text = new StringBuilder("🤖 ").append(name.apply(holder)).append(" plays **Trade**.");
        if (!free.isEmpty()) {
            text.append(" Free secondary (no command token) for: ")
                    .append(String.join(LIST, free))
                    .append('.');
        }
        if (!excluded.isEmpty())
            text.append(" Not offered: ").append(String.join(LIST, excluded)).append('.');
        for (String trader : traders) {
            text.append(' ').append(trader).append(" follows free anyway; I'll offer it an even wash.");
        }
        if (!free.isEmpty()) text.append('\n').append(TERMS);
        return text.toString();
    }

    private static String feeLabel(int fee) {
        return fee == 0 ? "X" : "X−" + fee;
    }
}
