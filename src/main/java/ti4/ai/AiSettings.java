package ti4.ai;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.SequencedMap;
import lombok.experimental.UtilityClass;
import ti4.ai.trace.ThinkingLimits;
import ti4.game.Game;
import ti4.game.Player;
import ti4.model.FactionModel;
import ti4.model.Mapper;
import ti4.settings.GlobalSettings;

@UtilityClass
public class AiSettings {

    public static final String NEKRO = "nekro";
    public static final SequencedMap<String, String> FACTION_NAMES = factionNames();
    public static final List<String> SUPPORTED_FACTIONS = List.copyOf(FACTION_NAMES.keySet());
    public static final List<String> DEFAULT_FACTIONS = List.of(NEKRO, "sardakk", "hacan", "letnev", "sol", "xxcha");
    public static final int MAX_SEATS = 8;
    public static final Duration POLL_PERIOD = Duration.ofSeconds(3);
    public static final Duration DEBOUNCE = Duration.ofMillis(2500);
    public static final Duration MIN_GAP_BETWEEN_ACTIONS = Duration.ofMillis(1500);
    public static final Duration WATCHDOG_PERIOD = Duration.ofMinutes(1);
    public static final Duration DORMANT_AFTER = Duration.ofDays(1);
    public static final Duration STALL_THRESHOLD = Duration.ofMinutes(3);
    public static final Duration REFEREE_DELAY = Duration.ofSeconds(20);
    public static final Duration DECIDE_TIMEOUT = Duration.ofSeconds(20);
    public static final Duration SLOW_DECISION = Duration.ofSeconds(2);
    public static final Duration SLOW_WARNING_GAP = Duration.ofMinutes(1);
    public static final Duration THINKING_RETRY = Duration.ofMinutes(5);
    public static final int THINKING_REENTRY_LIMIT = 8;
    public static final int THINKING_DEPTH_LIMIT = 60;
    public static final int THINKING_CALL_CAP = 200_000;
    public static final String THINKING_BUDGET_PROPERTY = "ai.thinking.budget.seconds";
    public static final int MAX_ATTEMPTS_PER_PROMPT = 2;
    public static final int MAX_ACTIONS_PER_HOUR = 150;
    public static final int MAX_DELEGATIONS_PER_ROUND = 30;
    public static final int MAX_GAME_DELEGATIONS_PER_ROUND = 60;
    public static final int MAX_REPEATED_FALLBACKS = 5;
    public static final int HISTORY_SIZE = 50;
    public static final int COMBAT_HISTORY_SIZE = 100;
    public static final String PACE_KEY = "aiPace";
    public static final String FAST_PACE = "fast";
    public static final String TRADING_PROPERTY = "ai.trading";
    public static final String COMBAT_CARDS_PROPERTY = "ai.combatcards";
    public static final String ACTION_CARDS_AB_PROPERTY = "ai.actioncards.ab";
    public static final String LEGACY_ACTION_CARDS_KEY = "aiLegacyActionCards";
    public static final String SECRET_HOLD_PROPERTY = "ai.secrets.hold";
    private static final Duration FAST_REFEREE_DELAY = Duration.ofSeconds(10);
    private static final Duration FAST_STALL_THRESHOLD = Duration.ofMinutes(1);
    private static final int FAST_MAX_ACTIONS_PER_HOUR = 600;

    private static SequencedMap<String, String> factionNames() {
        SequencedMap<String, String> names = new LinkedHashMap<>();
        names.put(NEKRO, "The Nekro Virus");
        names.put("sardakk", "Sardakk N'orr");
        names.put("hacan", "The Emirates of Hacan");
        names.put("letnev", "The Barony of Letnev");
        names.put("l1z1x", "The L1Z1X Mindnet");
        names.put("naalu", "The Naalu Collective");
        names.put("sol", "The Federation of Sol");
        names.put("jolnar", "The Universities of Jol-Nar");
        names.put("xxcha", "The Xxcha Kingdom");
        names.put("yin", "The Yin Brotherhood");
        return Collections.unmodifiableSequencedMap(names);
    }

    public static boolean isEnabled() {
        Object value = GlobalSettings.getSetting(
                GlobalSettings.ImplementedSettings.AI_PLAYERS_ENABLED.toString(), Object.class, false);
        if (value instanceof Boolean enabled) return enabled;
        return value != null && Boolean.parseBoolean(value.toString());
    }

    public static boolean isTradingEnabled() {
        return !"false".equalsIgnoreCase(System.getProperty(TRADING_PROPERTY, "true"));
    }

    public static boolean isCombatCardsEnabled() {
        return !"false".equalsIgnoreCase(System.getProperty(COMBAT_CARDS_PROPERTY, "true"));
    }

    public static boolean isActionCardAbTest() {
        return "true".equalsIgnoreCase(System.getProperty(ACTION_CARDS_AB_PROPERTY, "false"));
    }

    public static boolean usesLegacyActionCards(Player seat) {
        return Boolean.parseBoolean(seat.getStoredValue(LEGACY_ACTION_CARDS_KEY));
    }

    public static void markLegacyActionCards(Player seat) {
        seat.setStoredValue(LEGACY_ACTION_CARDS_KEY, "true");
    }

    public static boolean isSecretHoldEnabled() {
        return !"false".equalsIgnoreCase(System.getProperty(SECRET_HOLD_PROPERTY, "true"));
    }

    public static Duration thinkingBudget() {
        String configured = System.getProperty(THINKING_BUDGET_PROPERTY);
        if (configured == null || !configured.trim().matches("\\d+")) return DECIDE_TIMEOUT;
        return Duration.ofSeconds(Long.parseLong(configured.trim()));
    }

    public static ThinkingLimits thinkingLimits() {
        return new ThinkingLimits(
                thinkingBudget().toNanos(), THINKING_REENTRY_LIMIT, THINKING_DEPTH_LIMIT, THINKING_CALL_CAP);
    }

    public static boolean isFastPace(Game game) {
        return FAST_PACE.equals(game.getStoredValue(PACE_KEY)) && AiSeats.isSelfPlay(game);
    }

    public static Duration refereeDelay(Game game) {
        return isFastPace(game) ? FAST_REFEREE_DELAY : REFEREE_DELAY;
    }

    public static Duration stallThreshold(Game game) {
        return isFastPace(game) ? FAST_STALL_THRESHOLD : STALL_THRESHOLD;
    }

    public static int maxActionsPerHour(Game game) {
        return isFastPace(game) ? FAST_MAX_ACTIONS_PER_HOUR : MAX_ACTIONS_PER_HOUR;
    }

    public static boolean isSupportedFaction(String faction) {
        return SUPPORTED_FACTIONS.contains(faction);
    }

    public static String seatName(String faction) {
        FactionModel model = Mapper.getFaction(faction);
        String name = model == null || model.getShortName() == null ? faction : model.getShortName();
        return name + " AI";
    }

    public static String brainFor(String faction) {
        return NEKRO.equals(faction) ? NEKRO : "standard";
    }
}
