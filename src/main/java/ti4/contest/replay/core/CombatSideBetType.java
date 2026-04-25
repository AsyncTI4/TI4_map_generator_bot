package ti4.contest.replay.core;

import java.util.Locale;
import ti4.service.emoji.CardEmojis;
import ti4.service.emoji.DiceEmojis;
import ti4.service.emoji.TI4Emoji;
import ti4.service.emoji.UnitEmojis;

public enum CombatSideBetType {
    AFB_SKIPPED("Skips AFB", UnitEmojis.destroyer, 1, 6),
    AFB_WHIFF("AFB Whiff", UnitEmojis.destroyer, 2, 4),
    ROUND_ONE_WHIFF("R1 Whiff", DiceEmojis.d10red_1, 0, 10),
    ROUND_ONE_SLAM("R1 Slam", DiceEmojis.d10green_0, 0, 30),
    MORALE_BOOST("Plays Morale Boost", CardEmojis.ActionCard, 0, 8),
    SHIELDS_HOLDING("Plays Shields Holding", CardEmojis.ActionCard, 0, 8),
    WINNER_ONE_HP("Wins On 1\uFE0F\u20E3 HP", "1\uFE0F\u20E3", 0, 35);

    private final String label;
    private final TI4Emoji emoji;
    private final String unicodeEmoji;
    private final int requiredDestroyers;
    private final int profitPoints;

    CombatSideBetType(String label, TI4Emoji emoji, int requiredDestroyers, int profitPoints) {
        this.label = label;
        this.emoji = emoji;
        this.unicodeEmoji = null;
        this.requiredDestroyers = requiredDestroyers;
        this.profitPoints = profitPoints;
    }

    CombatSideBetType(String label, String unicodeEmoji, int requiredDestroyers, int profitPoints) {
        this.label = label;
        this.emoji = null;
        this.unicodeEmoji = unicodeEmoji;
        this.requiredDestroyers = requiredDestroyers;
        this.profitPoints = profitPoints;
    }

    public String key() {
        return name();
    }

    public String label() {
        return label;
    }

    public String emoji() {
        return emoji == null ? unicodeEmoji : emoji.toString();
    }

    public boolean isAvailable(int destroyerCount) {
        return destroyerCount >= requiredDestroyers;
    }

    public int profitPoints() {
        return profitPoints;
    }

    public static CombatSideBetType fromKey(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Unknown side bet type: " + key);
        }
        return CombatSideBetType.valueOf(key.trim().toUpperCase(Locale.ROOT));
    }
}
