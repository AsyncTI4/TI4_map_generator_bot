package ti4.ai.fallback;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.PromptButton;
import ti4.game.Game;
import ti4.game.Player;
import ti4.logging.BotLogger;

@UtilityClass
public class FallbackLog {

    public static final String LINE_PREFIX = "AI fallback ";
    public static final String KEY_PREFIX = "aiFallbackLog|";
    private static final String NEXT_KEY = KEY_PREFIX + "next";
    private static final String SLOT_KEY = KEY_PREFIX + "slot";
    private static final int CAPACITY = 10;
    private static final int TEXT_LENGTH = 200;
    private static final int BUTTONS_SHOWN = 12;
    private static final int BUTTON_LENGTH = 60;
    private static final int DECISION_LENGTH = 100;
    private static final String PRIVATE_TEXT = "(private prompt)";

    public static boolean isLogKey(String key) {
        return key.startsWith(KEY_PREFIX);
    }

    public static FallbackRecord capture(
            Game game,
            Player seat,
            AiPrompt prompt,
            String kind,
            String reason,
            List<String> recentDecisions,
            String pendingDecision,
            String outcome,
            long now) {
        boolean hidden = prompt != null && prompt.isHidden();
        return new FallbackRecord(
                now,
                game.getName(),
                game.getRound(),
                game.getPhaseOfGame(),
                seat.getFaction(),
                seat.getColor(),
                kind,
                reason,
                prompt == null ? "" : prompt.messageId(),
                hidden,
                promptText(prompt, hidden),
                buttonIds(prompt, hidden),
                recentDecisions.stream()
                        .map(decision -> StringUtils.abbreviate(decision, DECISION_LENGTH))
                        .toList(),
                StringUtils.abbreviate(pendingDecision, DECISION_LENGTH),
                outcome);
    }

    public static void record(Game game, FallbackRecord record) {
        BotLogger.info(record.logLine());
        int next = nextSlot(game);
        game.setStoredValue(SLOT_KEY + next, record.encode());
        game.setStoredValue(NEXT_KEY, String.valueOf((next + 1) % CAPACITY));
    }

    public static List<FallbackRecord> recent(Game game) {
        List<FallbackRecord> records = new ArrayList<>();
        int newest = nextSlot(game) + CAPACITY - 1;
        for (int age = 0; age < CAPACITY; age++) {
            int slot = (newest - age) % CAPACITY;
            FallbackRecord.decode(game.getStoredValue(SLOT_KEY + slot)).ifPresent(records::add);
        }
        return records;
    }

    private static int nextSlot(Game game) {
        String stored = game.getStoredValue(NEXT_KEY);
        return !stored.isEmpty() && StringUtils.isNumeric(stored) ? Integer.parseInt(stored) % CAPACITY : 0;
    }

    private static String promptText(AiPrompt prompt, boolean hidden) {
        if (prompt == null) return "";
        if (hidden) return PRIVATE_TEXT;
        return StringUtils.abbreviate(prompt.content().replace("\n", " "), TEXT_LENGTH);
    }

    private static List<String> buttonIds(AiPrompt prompt, boolean hidden) {
        if (prompt == null) return List.of();
        List<String> ids = new ArrayList<>();
        for (PromptButton button : prompt.buttons()) {
            if (ids.size() == BUTTONS_SHOWN) {
                ids.add("+" + (prompt.buttons().size() - BUTTONS_SHOWN) + " more");
                break;
            }
            String id = hidden ? button.handlerId() : button.customId();
            ids.add(StringUtils.abbreviate(id, BUTTON_LENGTH) + (button.disabled() ? " (disabled)" : ""));
        }
        return ids;
    }
}
