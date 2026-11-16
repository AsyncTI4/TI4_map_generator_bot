package ti4.ai.trade;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import ti4.ai.AiGameWrites;
import ti4.ai.AiPersonality;
import ti4.ai.Hostility;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.tactical.AttackLedger;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
public class Relations {

    static final int BAD = -1;
    static final int GOOD = 1;
    private static final double TRUSTED = 1.0;
    static final double TRADE_GOODS_PER_POINT = 2.0;
    static final int MAX = 2;
    static final String KEY = "aiRelations";
    private static final String EVENT_KEY = "relationsEvent|";
    private static final String SEPARATOR = "|";
    private static final String ENTRIES = ",";
    private static final String VALUE = ":";
    private static final String REASON = "AI relations change";

    static int level(Game game, Player seat, Player partner, double trust) {
        int level = AiPersonality.of(seat).startingRelations() + shift(seat, partner.getFaction());
        level -= hostility(game, seat, partner);
        if (AttackLedger.betrayalGrudge(game, seat, partner) > 0) level--;
        if (trust < Trust.LOWER_FEE) level--;
        return Math.min(level, MAX);
    }

    static int hostility(Game game, Player seat, Player partner) {
        return (int) Math.floor(Hostility.amount(game, seat, partner) / TRADE_GOODS_PER_POINT);
    }

    static boolean good(Game game, Player seat, Player partner, double trust) {
        return level(game, seat, partner, trust) >= GOOD;
    }

    public static boolean bad(Game game, Player seat, Player other) {
        if (seat == other || seat.getFaction().equals(other.getFaction())) return false;
        return level(game, seat, other, TRUSTED) <= BAD;
    }

    static void improve(AiTurnContext context, Player partner) {
        change(context, partner, 1);
    }

    static void worsen(AiTurnContext context, Player partner) {
        change(context, partner, -1);
    }

    private static void change(AiTurnContext context, Player partner, int step) {
        Game game = context.game();
        String theirs = partner.getFaction();
        String event = EVENT_KEY + game.getRound() + SEPARATOR + theirs + SEPARATOR + step;
        if (context.memory().has(event)) return;
        context.memory().put(event, "1");
        String ours = context.seat().getFaction();
        int start = AiPersonality.of(context.seat()).startingRelations();
        AiGameWrites.persist(game, REASON, latest -> {
            Player recorder = latest.getPlayerFromColorOrFaction(ours);
            if (recorder == null) return;
            Map<String, Integer> shifts = shifts(recorder);
            Player other = latest.getPlayerFromColorOrFaction(theirs);
            int hostility = other == null ? 0 : hostility(latest, recorder, other);
            int shifted = Math.min(shifts.getOrDefault(theirs, 0) + step, MAX - start + hostility);
            shifts.put(theirs, shifted);
            recorder.setStoredValue(KEY, encode(shifts));
        });
    }

    private static int shift(Player seat, String partner) {
        return shifts(seat).getOrDefault(partner, 0);
    }

    private static Map<String, Integer> shifts(Player seat) {
        Map<String, Integer> shifts = new LinkedHashMap<>();
        for (String entry : StringUtils.split(seat.getStoredValue(KEY), ENTRIES)) {
            String faction = StringUtils.substringBefore(entry, VALUE).trim();
            String value = StringUtils.substringAfter(entry, VALUE).trim();
            if (!faction.isEmpty() && NumberUtils.isCreatable(value)) shifts.put(faction, Integer.parseInt(value));
        }
        return shifts;
    }

    private static String encode(Map<String, Integer> shifts) {
        return shifts.entrySet().stream()
                .map(entry -> entry.getKey() + VALUE + entry.getValue())
                .collect(Collectors.joining(ENTRIES));
    }
}
