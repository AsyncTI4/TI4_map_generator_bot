package ti4.contest.replay.core;

import lombok.experimental.UtilityClass;

@UtilityClass
public class CombatReplayPromotionScoreSupport {

    private static final double BLOWOUT_PENALTY_MULTIPLIER = 0.75;

    public double computePromotionScore(double attackerLossRatio, double defenderLossRatio, int roundsObserved) {
        double mutualLossScore =
                Math.min(attackerLossRatio, defenderLossRatio) + ((attackerLossRatio + defenderLossRatio) / 2.0);
        double blowoutPenalty = BLOWOUT_PENALTY_MULTIPLIER * Math.abs(attackerLossRatio - defenderLossRatio);
        return roundsObserved + mutualLossScore - blowoutPenalty;
    }

    public double computeLossRatio(double initialStrength, double remainingStrength) {
        if (initialStrength <= 0) return 0.0;
        return Math.max(0.0, Math.min(1.0, (initialStrength - remainingStrength) / initialStrength));
    }
}
