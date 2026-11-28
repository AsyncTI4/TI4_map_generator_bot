package ti4.ai.strategy;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.ai.trace.AiDebugLog;
import ti4.ai.trace.AiLogStore;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
public class StrategyCardPickLog {

    public static final String KEY_PREFIX = "aiScPickLog|";
    private static final String ENTRY_SEPARATOR = " | ";
    private static final String BENEFIT_SEPARATOR = " / ";
    private static final double MIN_SHOWN = 0.005;

    public static void record(Game game, Player seat, List<StrategyCardRanking.Pick> ranked) {
        if (ranked.isEmpty()) return;
        String ranking = describe(ranked);
        String key = key(game.getRound(), seat.getFaction());
        AiLogStore.put(game.getName(), key, ranking);
        AiDebugLog.log(game, seat, "ranked strategy cards:\n" + String.join("\n", describeEach(ranked)));
    }

    public static Optional<String> recorded(Game game, int round, String faction) {
        return Optional.ofNullable(AiLogStore.get(game.getName(), key(round, faction)))
                .filter(StringUtils::isNotBlank);
    }

    private static List<String> describeEach(List<StrategyCardRanking.Pick> ranked) {
        return ranked.stream().map(StrategyCardPickLog::describe).toList();
    }

    static String describe(List<StrategyCardRanking.Pick> ranked) {
        return String.join(
                ENTRY_SEPARATOR,
                ranked.stream().map(StrategyCardPickLog::describe).toList());
    }

    private static String describe(StrategyCardRanking.Pick pick) {
        CardPlan plan = pick.plan();
        List<String> parts = new ArrayList<>(plan.benefits().stream()
                .filter(benefit -> benefit.value() != 0)
                .sorted(Comparator.comparingDouble(CardPlan.Benefit::value).reversed())
                .map(benefit -> benefit.use().label() + " " + signed(benefit.value()))
                .toList());
        if (pick.race() >= MIN_SHOWN) parts.add("act early in the custodians race " + signed(pick.race()));
        double adjustments = pick.value() - plan.value() - pick.race();
        if (Math.abs(adjustments) >= MIN_SHOWN) parts.add("prior/tg/denial " + signed(adjustments));
        String name = StringUtils.capitalize(plan.card().name().toLowerCase(Locale.ROOT));
        return clean(plan.initiative() + " " + name + " " + number(pick.value()) + " ["
                + String.join(BENEFIT_SEPARATOR, parts) + "]");
    }

    private static String key(int round, String faction) {
        return KEY_PREFIX + "r" + round + "|" + faction;
    }

    private static String signed(double value) {
        return (value >= 0 ? "+" : "") + number(value);
    }

    private static String number(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static String clean(String text) {
        return text.replace(",", "").replace(":", "").replace(";", "");
    }
}
