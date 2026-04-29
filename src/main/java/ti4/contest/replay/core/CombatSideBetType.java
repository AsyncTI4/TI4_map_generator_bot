package ti4.contest.replay.core;

import java.util.Locale;
import ti4.service.emoji.CardEmojis;
import ti4.service.emoji.DiceEmojis;
import ti4.service.emoji.TI4Emoji;
import ti4.service.emoji.UnitEmojis;

/**
 * Defines the supported combat side bets, their labels, availability gates, and legacy flat payouts.
 */
public enum CombatSideBetType {
    /** Current AFB side bet: player skips available anti-fighter barrage. */
    AFB_SKIPPED("Skips AFB", UnitEmojis.destroyer, 1, 6),
    /** Current AFB side bet: player rolls AFB and scores zero hits. */
    AFB_WHIFF("AFB Whiff", UnitEmojis.destroyer, 0, 4),
    /** Current dynamic side bet: player scores zero hits in their first combat roll. */
    ROUND_ONE_WHIFF("R1 Whiff", DiceEmojis.d10red_1, 0, 10),
    /** Current dynamic side bet: every die in player's first combat roll hits. */
    ROUND_ONE_SLAM("R1 Slam", DiceEmojis.d10green_0, 0, 30),
    /** Current tracked interaction side bet. */
    MORALE_BOOST("Plays Morale Boost", CardEmojis.ActionCard, 0, 8),
    /** Current tracked interaction side bet. */
    SHIELDS_HOLDING("Plays Shields Holding", CardEmojis.ActionCard, 0, 8),
    /** Current tracked interaction side bet. */
    DIRECT_HIT("Plays Direct Hit", CardEmojis.ActionCard, 0, 8),
    /** Current tracked interaction side bet. */
    FIGHTER_PROTOTYPE("Plays Fighter Prototype", CardEmojis.ActionCard, 0, 24),
    /** Current dynamic resolution side bet. */
    WINNER_ONE_HP("Wins On 1\uFE0F\u20E3 HP", "1\uFE0F\u20E3", 0, 35);

    private final String label;
    private final TI4Emoji emoji;
    private final String unicodeEmoji;
    private final int requiredDestroyers;
    private final int profitPoints;

    CombatSideBetType(String label, TI4Emoji emoji, int requiredDestroyers, int profitPoints) {
        this.label = label;
        this.emoji = emoji;
        unicodeEmoji = null;
        this.requiredDestroyers = requiredDestroyers;
        this.profitPoints = profitPoints;
    }

    CombatSideBetType(String label, String unicodeEmoji, int requiredDestroyers, int profitPoints) {
        this.label = label;
        emoji = null;
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
        return valueOf(key.trim().toUpperCase(Locale.ROOT));
    }
}
