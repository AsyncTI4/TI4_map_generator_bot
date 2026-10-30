package ti4.ai.letnev;

import lombok.experimental.UtilityClass;
import ti4.ai.brain.StrategyCard;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
public class GravleashPlan {

    public static final String BREAKTHROUGH = "letnevbt";
    public static final String HERO = "letnevhero";
    public static final String GRAVITY_DRIVE = "gd";
    static final double CARD_BONUS = 6.0;
    static final double GRAVITY_DRIVE_BONUS = 8.0;
    public static final double SHIP_TYPE_VALUE = 1.0;

    public static boolean hasBreakthrough(Player seat) {
        return seat.hasUnlockedBreakthrough(BREAKTHROUGH);
    }

    public static double cardBonus(Game game, Player seat, StrategyCard card) {
        if (!seat.hasLeaderUnlocked(HERO)) return 0;
        boolean wanted = card == StrategyCard.IMPERIAL || (card == StrategyCard.WARFARE && hasBreakthrough(seat));
        return wanted ? CARD_BONUS : 0;
    }

    public static boolean holdsUnplayed(Game game, Player seat, StrategyCard card) {
        return seat.getSCs().stream()
                .anyMatch(initiative -> StrategyCard.of(game, initiative) == card
                        && !game.getPlayedSCs().contains(initiative));
    }

    public static double driveBonus(Player seat, String tech) {
        return GRAVITY_DRIVE.equals(tech) && hasBreakthrough(seat) ? GRAVITY_DRIVE_BONUS : 0;
    }
}
