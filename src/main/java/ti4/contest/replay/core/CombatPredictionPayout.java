package ti4.contest.replay.core;

import lombok.experimental.UtilityClass;

/**
 * Calculates prediction profit from the crowd split, rewarding even calls and underdog wins.
 */
@UtilityClass
public class CombatPredictionPayout {

    public int points(int winnerPredictions, int totalPredictions) {
        if (winnerPredictions <= 0 || totalPredictions <= 0) return 0;

        double winnerShare = winnerPredictions / (double) totalPredictions;
        double evenness = 4.0 * winnerShare * (1.0 - winnerShare);
        double underdog = Math.max(0.0, 0.5 - winnerShare) / 0.5;
        double points = 4.0 + 12.0 * evenness + 70.0 * Math.pow(underdog, 1.4);
        return (int) Math.round(Math.clamp(points, 4.0, 100.0));
    }
}
