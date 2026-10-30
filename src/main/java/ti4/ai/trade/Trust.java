package ti4.ai.trade;

import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import ti4.ai.AiSeats;
import ti4.ai.brain.AiMemory;
import ti4.ai.brain.AiTurnContext;
import ti4.game.Player;

@UtilityClass
class Trust {

    private static final double AI = 1.0;
    private static final double HUMAN_START = 0.8;
    static final double FREE_FOLLOW = 0.5;
    static final double LOWER_FEE = 0.8;
    static final double LENDING = 0.6;
    static final double UNSOLICITED = 0.5;
    static final String KEY = "tradeTrust";
    static final String ROUND_KEY = "tradeTrustRound";
    private static final double MIN = 0;
    private static final double MAX = 1;
    private static final String ENTRIES = ",";
    private static final String VALUE = "=";
    private static final String FORMAT = "%.2f";

    enum Event {
        ACCEPTED_OFFER(0.1),
        PAID_DEBT(0.1),
        FREE_FOLLOW_UNPAID(-0.3),
        EXCLUDED_REPLENISH_UNPAID(-0.3),
        COLLECTION_UNPAID(-0.2);

        private final double delta;

        Event(double delta) {
            this.delta = delta;
        }

        double delta() {
            return delta;
        }
    }

    static double of(AiTurnContext context, Player player) {
        if (AiSeats.isAiSeat(player)) return AI;
        return read(context.memory()).getOrDefault(player.getFaction(), HUMAN_START);
    }

    static void record(AiTurnContext context, Player player, Event event) {
        adjust(context, player, event.delta());
    }

    static void adjust(AiTurnContext context, Player player, double delta) {
        if (AiSeats.isAiSeat(player)) return;
        Map<String, Double> trust = read(context.memory());
        double current = trust.getOrDefault(player.getFaction(), HUMAN_START);
        trust.put(player.getFaction(), Math.clamp(current + delta, MIN, MAX));
        rewrite(context.memory(), KEY, encode(trust));
    }

    static void refresh(AiTurnContext context) {
        AiMemory memory = context.memory();
        String round = String.valueOf(context.game().getRound());
        if (round.equals(memory.get(ROUND_KEY).orElse(""))) return;
        memory.get(KEY).ifPresent(value -> rewrite(memory, KEY, value));
        rewrite(memory, ROUND_KEY, round);
    }

    private static Map<String, Double> read(AiMemory memory) {
        Map<String, Double> trust = new TreeMap<>();
        for (String entry : StringUtils.split(memory.get(KEY).orElse(""), ENTRIES)) {
            String faction = StringUtils.substringBefore(entry, VALUE);
            String value = StringUtils.substringAfter(entry, VALUE);
            if (!faction.isEmpty() && NumberUtils.isCreatable(value)) {
                trust.put(faction, Math.clamp(Double.parseDouble(value), MIN, MAX));
            }
        }
        return trust;
    }

    private static String encode(Map<String, Double> trust) {
        return trust.entrySet().stream()
                .map(entry -> entry.getKey() + VALUE + String.format(Locale.ROOT, FORMAT, entry.getValue()))
                .collect(Collectors.joining(ENTRIES));
    }

    private static void rewrite(AiMemory memory, String key, String value) {
        memory.remove(key);
        memory.put(key, value);
    }
}
