package ti4.contest.replay.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import ti4.contest.replay.core.CombatPredictionPayout;
import ti4.contest.replay.entities.CombatReplayLeaderboardEntryEntity;

/**
 * Pure scoring helper for prediction payouts and winner result summaries.
 */
final class CombatReplayPredictionScorer {

    static ScoredPredictions score(
            List<LockedPrediction> attackerPredictions,
            List<LockedPrediction> defenderPredictions,
            String winnerFaction,
            String attackerFaction) {
        List<LockedPrediction> winningPredictions = List.of();
        if (winnerFaction != null) {
            winningPredictions =
                    winnerFaction.equalsIgnoreCase(attackerFaction) ? attackerPredictions : defenderPredictions;
        }
        List<LockedPrediction> allPredictions =
                new ArrayList<>(attackerPredictions.size() + defenderPredictions.size());
        allPredictions.addAll(attackerPredictions);
        allPredictions.addAll(defenderPredictions);

        int winnerPredictions = winningPredictions.size();
        int totalPredictions = allPredictions.size();
        int pointsAwarded =
                winnerPredictions == 0 ? 0 : CombatPredictionPayout.points(winnerPredictions, totalPredictions);
        return new ScoredPredictions(totalPredictions, pointsAwarded, allPredictions, winningPredictions);
    }

    static List<WinningPredictionSummary> resultSummaries(
            List<LockedPrediction> winningPredictions,
            int predictionPointsAwarded,
            Map<String, CombatReplayLeaderboardEntryEntity> entriesByUser) {
        List<WinningPredictionSummary> summaries = new ArrayList<>(winningPredictions.size());
        for (LockedPrediction prediction : winningPredictions) {
            CombatReplayLeaderboardEntryEntity entry = entriesByUser.get(prediction.discordUserId());
            summaries.add(new WinningPredictionSummary(
                    prediction.discordUserId(),
                    prediction.discordUserName(),
                    predictionPointsAwarded,
                    entry == null ? 0 : safeInt(entry.getTotalPoints())));
        }
        summaries.sort((left, right) -> {
            int totalPointsComparison = Integer.compare(right.totalPoints(), left.totalPoints());
            if (totalPointsComparison != 0) return totalPointsComparison;
            return left.discordUserName().compareToIgnoreCase(right.discordUserName());
        });
        return summaries;
    }

    private static int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    record LockedPrediction(String discordUserId, String discordUserName) {}

    record ScoredPredictions(
            int totalPredictions,
            int pointsAwarded,
            List<LockedPrediction> allPredictions,
            List<LockedPrediction> winningPredictions) {}

    record WinningPredictionSummary(String discordUserId, String discordUserName, int pointsAwarded, int totalPoints) {}
}
