package ti4.contest.replay.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import ti4.contest.replay.core.CombatPredictionPayout;
import ti4.contest.replay.entities.CombatReplayLeaderboardEntryEntity;

/**
 * Pure scoring helper for prediction payouts and winner result summaries.
 */
final class CombatReplayPredictionScorer {

    static ScoredPredictions score(
            List<LockedPrediction> attackerPredictions,
            List<LockedPrediction> defenderPredictions,
            Set<String> attackerDoubleOrBustUserIds,
            Set<String> defenderDoubleOrBustUserIds,
            String winnerFaction,
            String attackerFaction) {
        List<LockedPrediction> winningPredictions = List.of();
        List<LockedPrediction> losingPredictions = List.of();
        Set<String> winningDoubleOrBustUserIds = Set.of();
        Set<String> losingDoubleOrBustUserIds = Set.of();
        if (winnerFaction != null) {
            boolean attackerWon = winnerFaction.equalsIgnoreCase(attackerFaction);
            winningPredictions = attackerWon ? attackerPredictions : defenderPredictions;
            losingPredictions = attackerWon ? defenderPredictions : attackerPredictions;
            winningDoubleOrBustUserIds = attackerWon ? attackerDoubleOrBustUserIds : defenderDoubleOrBustUserIds;
            losingDoubleOrBustUserIds = attackerWon ? defenderDoubleOrBustUserIds : attackerDoubleOrBustUserIds;
        }
        List<LockedPrediction> allPredictions =
                new ArrayList<>(attackerPredictions.size() + defenderPredictions.size());
        allPredictions.addAll(attackerPredictions);
        allPredictions.addAll(defenderPredictions);

        int winnerPredictions = winningPredictions.size();
        int totalPredictions = allPredictions.size();
        int pointsAwarded =
                winnerPredictions == 0 ? 0 : CombatPredictionPayout.points(winnerPredictions, totalPredictions);
        int attackerWouldHaveWonPoints = attackerPredictions.isEmpty()
                ? 0
                : CombatPredictionPayout.points(attackerPredictions.size(), totalPredictions);
        int defenderWouldHaveWonPoints = defenderPredictions.isEmpty()
                ? 0
                : CombatPredictionPayout.points(defenderPredictions.size(), totalPredictions);
        int bustPenalty = winnerFaction != null && winnerFaction.equalsIgnoreCase(attackerFaction)
                ? defenderWouldHaveWonPoints
                : attackerWouldHaveWonPoints;
        return new ScoredPredictions(
                totalPredictions,
                pointsAwarded,
                allPredictions,
                winningPredictions,
                losingPredictions,
                safeSet(winningDoubleOrBustUserIds),
                safeSet(losingDoubleOrBustUserIds),
                bustPenalty);
    }

    static List<WinningPredictionSummary> resultSummaries(
            List<LockedPrediction> winningPredictions,
            int predictionPointsAwarded,
            Set<String> winningDoubleOrBustUserIds,
            Map<String, CombatReplayLeaderboardEntryEntity> entriesByUser) {
        List<WinningPredictionSummary> summaries = new ArrayList<>(winningPredictions.size());
        for (LockedPrediction prediction : winningPredictions) {
            CombatReplayLeaderboardEntryEntity entry = entriesByUser.get(prediction.discordUserId());
            boolean doubled = winningDoubleOrBustUserIds != null
                    && winningDoubleOrBustUserIds.contains(prediction.discordUserId());
            summaries.add(new WinningPredictionSummary(
                    prediction.discordUserId(),
                    prediction.discordUserName(),
                    doubled ? predictionPointsAwarded * 2 : predictionPointsAwarded,
                    doubled,
                    entry == null ? 0 : safeInt(entry.getTotalPoints())));
        }
        summaries.sort((left, right) -> {
            int totalPointsComparison = Integer.compare(right.totalPoints(), left.totalPoints());
            if (totalPointsComparison != 0) return totalPointsComparison;
            return left.discordUserName().compareToIgnoreCase(right.discordUserName());
        });
        return summaries;
    }

    static List<BustedPredictionSummary> bustedSummaries(
            List<LockedPrediction> losingPredictions,
            Set<String> losingDoubleOrBustUserIds,
            int bustPenalty,
            Map<String, CombatReplayLeaderboardEntryEntity> entriesByUser) {
        if (losingDoubleOrBustUserIds == null || losingDoubleOrBustUserIds.isEmpty()) return List.of();
        List<BustedPredictionSummary> summaries = new ArrayList<>();
        int pointsLost = 4 + bustPenalty;
        for (LockedPrediction prediction : losingPredictions) {
            if (!losingDoubleOrBustUserIds.contains(prediction.discordUserId())) continue;
            CombatReplayLeaderboardEntryEntity entry = entriesByUser.get(prediction.discordUserId());
            summaries.add(new BustedPredictionSummary(
                    prediction.discordUserId(),
                    prediction.discordUserName(),
                    pointsLost,
                    entry == null ? 0 : safeInt(entry.getTotalPoints())));
        }
        summaries.sort((left, right) -> {
            int pointsLostComparison = Integer.compare(right.pointsLost(), left.pointsLost());
            if (pointsLostComparison != 0) return pointsLostComparison;
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
            List<LockedPrediction> winningPredictions,
            List<LockedPrediction> losingPredictions,
            Set<String> winningDoubleOrBustUserIds,
            Set<String> losingDoubleOrBustUserIds,
            int bustPenalty) {}

    record WinningPredictionSummary(
            String discordUserId, String discordUserName, int pointsAwarded, boolean doubled, int totalPoints) {}

    record BustedPredictionSummary(String discordUserId, String discordUserName, int pointsLost, int totalPoints) {}

    private static Set<String> safeSet(Set<String> value) {
        return value == null ? Set.of() : new HashSet<>(value);
    }
}
