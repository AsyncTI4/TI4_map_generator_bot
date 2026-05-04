package ti4.contest.replay.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ti4.contest.replay.core.CombatContestSettings;
import ti4.contest.replay.core.CombatReplayHouse;
import ti4.contest.replay.entities.CombatContestSideBetEntity;
import ti4.contest.replay.entities.CombatReplayContestEntity;
import ti4.contest.replay.entities.CombatReplayHouseEntity;
import ti4.contest.replay.entities.CombatReplayHouseScoreEntity;
import ti4.contest.replay.house.hacan.CombatReplayHacanMarketCompactService;
import ti4.contest.replay.house.hacan.CombatReplayHacanTradeConvoysService;
import ti4.contest.replay.repository.CombatContestSideBetRepository;
import ti4.contest.replay.repository.CombatReplayHouseScoreRepository;
import ti4.contest.replay.service.CombatReplayHouseScoringContext.HouseTotals;
import ti4.contest.replay.service.CombatReplayPredictionScorer.LockedPrediction;
import ti4.contest.replay.service.CombatReplayPredictionScorer.ScoredPredictions;
import ti4.contest.replay.service.CombatReplaySideBetService.ResolvedSideBet;
import ti4.json.JsonMapperManager;

@Service
@RequiredArgsConstructor
public class CombatReplayHouseLedgerService {

    private static final long SEASON_OPENING_BALANCE_CONTEST_ID = 0L;
    private static final int WRONG_PREDICTION_PENALTY = -4;

    private final CombatContestSettings settings;
    private final CombatContestSideBetRepository sideBetRepository;
    private final CombatReplayHouseScoreRepository houseScoreRepository;
    private final CombatReplayHouseService houseService;
    private final CombatReplayHacanMarketCompactService hacanMarketCompactService;
    private final CombatReplayHacanTradeConvoysService hacanTradeConvoysService;
    private final CombatReplayCustodianFavorScoringRule custodianFavorScoringRule;
    private final List<CombatReplayHouseScoringRule> houseScoringRules;

    public void clearContest(Long contestId) {
        if (contestId == null) return;
        houseScoreRepository.deleteAll(houseScoreRepository.findByContestId(contestId));
        hacanMarketCompactService.clear(contestId);
        hacanTradeConvoysService.clearTradeConvoys(contestId);
    }

    public void ensureSeasonOpeningBalances() {
        if (!settings.isHousesEnabled()) return;

        writeSeasonOpeningBalances();
    }

    public void resetSeasonOpeningBalances() {
        if (!settings.isHousesEnabled()) return;

        LocalDateTime now = LocalDateTime.now();
        List<CombatReplayHouseScoreEntity> scores = houseScoreRepository.findAll();
        for (CombatReplayHouseScoreEntity score : scores) {
            score.setPredictionPoints(0);
            score.setSideBetPoints(0);
            score.setAbilityPoints(0);
            score.setFavorPoints(0);
            score.setTotalPoints(0);
            score.setPredictionCount(0);
            score.setCorrectPredictions(0);
            score.setAbilityBreakdownJson(writeHouseAbilitySummaries(List.of()));
            score.setScoredAt(now);
        }
        houseScoreRepository.saveAll(scores);
        writeSeasonOpeningBalances();
    }

    private void writeSeasonOpeningBalances() {
        Map<CombatReplayHouse, CombatReplayHouseScoreEntity> existingScoresByHouse =
                new EnumMap<>(CombatReplayHouse.class);
        for (CombatReplayHouseScoreEntity score :
                houseScoreRepository.findByContestId(SEASON_OPENING_BALANCE_CONTEST_ID)) {
            existingScoresByHouse.put(score.getHouse(), score);
        }

        LocalDateTime now = LocalDateTime.now();
        List<CombatReplayHouseScoreEntity> scores = new ArrayList<>();
        for (CombatReplayHouse house : CombatReplayHouse.assignmentOrder()) {
            CombatReplayHouseScoreEntity score =
                    existingScoresByHouse.getOrDefault(house, new CombatReplayHouseScoreEntity());
            score.setContestId(SEASON_OPENING_BALANCE_CONTEST_ID);
            score.setHouse(house);
            score.setPredictionPoints(0);
            score.setSideBetPoints(0);
            score.setAbilityPoints(0);
            score.setFavorPoints(0);
            score.setTotalPoints(settings.getHouseAbilities().getInitialHousePoints());
            score.setPredictionCount(0);
            score.setCorrectPredictions(0);
            score.setAbilityBreakdownJson(writeHouseAbilitySummaries(List.of()));
            score.setScoredAt(now);
            scores.add(score);
        }
        houseScoreRepository.saveAll(scores);
    }

    public List<HouseLeaderboardSummary> leaderboardSummaries() {
        List<CombatReplayHouseEntity> houseAssignments = houseService.allHouseAssignments();
        if (houseAssignments.isEmpty()) return List.of();

        EnumMap<CombatReplayHouse, HouseTotals> totalsByHouse = emptyTotalsByHouse();
        for (CombatReplayHouseEntity assignment : houseAssignments) {
            HouseTotals totals = totalsByHouse.get(assignment.getHouse());
            totals.memberCount++;
        }

        for (CombatReplayHouseScoreEntity score : houseScoreRepository.findAll()) {
            HouseTotals totals = totalsByHouse.get(score.getHouse());
            if (totals == null) continue;
            totals.predictionPoints += safeInt(score.getPredictionPoints());
            totals.sideBetPoints += safeInt(score.getSideBetPoints());
            totals.abilityPoints += safeInt(score.getAbilityPoints());
            totals.totalPoints += safeInt(score.getTotalPoints());
            totals.predictionCount += safeInt(score.getPredictionCount());
            totals.correctPredictions += safeInt(score.getCorrectPredictions());
        }

        List<HouseLeaderboardSummary> summaries = new ArrayList<>();
        for (Map.Entry<CombatReplayHouse, HouseTotals> entry : totalsByHouse.entrySet()) {
            HouseTotals totals = entry.getValue();
            summaries.add(new HouseLeaderboardSummary(
                    entry.getKey(),
                    totals.totalPoints,
                    totals.predictionCount,
                    totals.correctPredictions,
                    totals.memberCount,
                    totals.abilityPoints));
        }
        summaries.sort(Comparator.comparingInt(HouseLeaderboardSummary::totalPoints)
                .thenComparingInt(HouseLeaderboardSummary::correctPredictions)
                .thenComparingInt(HouseLeaderboardSummary::predictionCount)
                .reversed()
                .thenComparing(summary -> summary.house().displayName()));
        return summaries;
    }

    public List<HousePredictionSummary> buildAndPersistPredictionSummaries(
            CombatReplayContestEntity replayContest,
            ScoredPredictions scoredPredictions,
            List<ResolvedSideBet> resolvedSideBets) {
        List<HousePredictionSummary> summaries =
                buildPredictionSummaries(replayContest, scoredPredictions, resolvedSideBets);
        persistPredictionSummaries(replayContest, summaries);
        return summaries;
    }

    public List<HousePredictionSummary> readPredictionSummaries(Long contestId) {
        if (!settings.isHousesEnabled() || contestId == null) return List.of();
        List<HousePredictionSummary> summaries = new ArrayList<>();
        for (CombatReplayHouseScoreEntity score : houseScoreRepository.findByContestId(contestId)) {
            int favorPoints = safeInt(score.getFavorPoints());
            summaries.add(new HousePredictionSummary(
                    score.getHouse(),
                    safeInt(score.getPredictionPoints()),
                    safeInt(score.getSideBetPoints()),
                    safeInt(score.getAbilityPoints()),
                    favorPoints,
                    safeInt(score.getPredictionCount()),
                    safeInt(score.getCorrectPredictions()),
                    readHouseAbilitySummaries(score.getAbilityBreakdownJson()),
                    favorPoints == 0
                            ? List.of()
                            : List.of(new HouseFavorSummary(
                                    "the Custodians sealing this combat's ledger", favorPoints))));
        }
        sortPredictionSummaries(summaries);
        return summaries;
    }

    private List<HousePredictionSummary> buildPredictionSummaries(
            CombatReplayContestEntity replayContest,
            ScoredPredictions scoredPredictions,
            List<ResolvedSideBet> resolvedSideBets) {
        if (!settings.isHousesEnabled() || replayContest == null || replayContest.getId() == null) return List.of();

        Set<String> winningUserIds = new HashSet<>();
        Set<String> predictionUserIds = new HashSet<>();
        List<LockedPrediction> allPredictions =
                scoredPredictions == null ? List.of() : scoredPredictions.allPredictions();
        List<LockedPrediction> winningPredictions =
                scoredPredictions == null ? List.of() : scoredPredictions.winningPredictions();
        for (LockedPrediction prediction : winningPredictions) {
            winningUserIds.add(prediction.discordUserId());
        }
        for (LockedPrediction prediction : allPredictions) {
            predictionUserIds.add(prediction.discordUserId());
        }

        List<CombatContestSideBetEntity> sideBets = sideBetRepository.findByContestId(replayContest.getId());
        Set<String> userIds = new HashSet<>(predictionUserIds);
        for (CombatContestSideBetEntity sideBet : sideBets) {
            userIds.add(sideBet.getDiscordUserId());
        }

        Map<String, CombatReplayHouse> housesByUser = houseService.housesByUserIds(userIds);
        if (housesByUser.isEmpty()) return List.of();

        EnumMap<CombatReplayHouse, HouseTotals> totalsByHouse = emptyTotalsByHouse();
        EnumMap<CombatReplayHouse, List<HouseAbilitySummary>> abilitiesByHouse = new EnumMap<>(CombatReplayHouse.class);
        EnumMap<CombatReplayHouse, List<HouseFavorSummary>> favorsByHouse = new EnumMap<>(CombatReplayHouse.class);
        for (CombatReplayHouse house : CombatReplayHouse.assignmentOrder()) {
            abilitiesByHouse.put(house, new ArrayList<>());
            favorsByHouse.put(house, new ArrayList<>());
        }

        applyPredictionLedger(scoredPredictions, allPredictions, winningUserIds, housesByUser, totalsByHouse);
        applySideBetLedger(sideBets, resolvedSideBets, housesByUser, totalsByHouse);
        CombatReplayHouseScoringContext scoringContext = new CombatReplayHouseScoringContext(
                replayContest,
                allPredictions,
                winningUserIds,
                sideBets,
                resolvedSideBets,
                housesByUser,
                totalsByHouse,
                abilitiesByHouse,
                favorsByHouse);
        for (CombatReplayHouseScoringRule scoringRule : houseScoringRules) {
            scoringRule.apply(scoringContext);
        }

        EnumMap<CombatReplayHouse, Integer> currentAbilityPointsByHouse = currentAbilityPoints(abilitiesByHouse);
        List<HousePredictionSummary> summaries = new ArrayList<>();
        for (Map.Entry<CombatReplayHouse, HouseTotals> entry : totalsByHouse.entrySet()) {
            HouseTotals totals = entry.getValue();
            CombatReplayHouse house = entry.getKey();
            summaries.add(new HousePredictionSummary(
                    house,
                    totals.predictionPoints,
                    totals.sideBetPoints,
                    currentAbilityPointsByHouse.getOrDefault(house, 0),
                    totals.favorPoints,
                    totals.predictionCount,
                    totals.correctPredictions,
                    abilitiesByHouse.getOrDefault(house, List.of()),
                    favorsByHouse.getOrDefault(house, List.of())));
        }
        sortPredictionSummaries(summaries);
        return summaries;
    }

    private void applyPredictionLedger(
            ScoredPredictions scoredPredictions,
            List<LockedPrediction> allPredictions,
            Set<String> winningUserIds,
            Map<String, CombatReplayHouse> housesByUser,
            EnumMap<CombatReplayHouse, HouseTotals> totalsByHouse) {
        for (LockedPrediction prediction : allPredictions) {
            CombatReplayHouse house = housesByUser.get(prediction.discordUserId());
            if (house == null) continue;
            boolean correct = winningUserIds.contains(prediction.discordUserId());
            HouseTotals totals = totalsByHouse.get(house);
            totals.predictionPoints += correct ? scoredPredictions.pointsAwarded() : WRONG_PREDICTION_PENALTY;
            totals.predictionCount++;
            if (correct) {
                totals.correctPredictions++;
            }
        }
    }

    private void applySideBetLedger(
            List<CombatContestSideBetEntity> sideBets,
            List<ResolvedSideBet> resolvedSideBets,
            Map<String, CombatReplayHouse> housesByUser,
            EnumMap<CombatReplayHouse, HouseTotals> totalsByHouse) {
        int sideBetCost = settings.getSideBets().getCostPoints();
        for (CombatContestSideBetEntity sideBet : sideBets) {
            CombatReplayHouse house = housesByUser.get(sideBet.getDiscordUserId());
            if (house == null) continue;
            totalsByHouse.get(house).sideBetPoints -= sideBetCost;
        }

        for (ResolvedSideBet sideBet : resolvedSideBets == null ? List.<ResolvedSideBet>of() : resolvedSideBets) {
            CombatReplayHouse house = housesByUser.get(sideBet.discordUserId());
            if (house == null) continue;
            totalsByHouse.get(house).sideBetPoints += sideBet.profitPoints();
        }
    }

    private EnumMap<CombatReplayHouse, Integer> currentAbilityPoints(
            EnumMap<CombatReplayHouse, List<HouseAbilitySummary>> abilitiesByHouse) {
        EnumMap<CombatReplayHouse, Integer> currentAbilityPointsByHouse = new EnumMap<>(CombatReplayHouse.class);
        for (CombatReplayHouse house : CombatReplayHouse.assignmentOrder()) {
            currentAbilityPointsByHouse.put(
                    house,
                    abilitiesByHouse.getOrDefault(house, List.of()).stream()
                            .mapToInt(HouseAbilitySummary::points)
                            .sum());
        }
        return currentAbilityPointsByHouse;
    }

    public int combatFavorGain(int pointsBehindLeader) {
        return custodianFavorScoringRule.combatFavorGain(pointsBehindLeader);
    }

    private void persistPredictionSummaries(
            CombatReplayContestEntity replayContest, List<HousePredictionSummary> summaries) {
        if (!settings.isHousesEnabled() || replayContest == null || replayContest.getId() == null) return;

        Map<CombatReplayHouse, CombatReplayHouseScoreEntity> existingScoresByHouse =
                new EnumMap<>(CombatReplayHouse.class);
        for (CombatReplayHouseScoreEntity score : houseScoreRepository.findByContestId(replayContest.getId())) {
            existingScoresByHouse.put(score.getHouse(), score);
        }

        LocalDateTime now = LocalDateTime.now();
        List<CombatReplayHouseScoreEntity> scores = new ArrayList<>();
        for (HousePredictionSummary summary : summaries) {
            CombatReplayHouseScoreEntity score =
                    existingScoresByHouse.getOrDefault(summary.house(), new CombatReplayHouseScoreEntity());
            score.setContestId(replayContest.getId());
            score.setHouse(summary.house());
            score.setPredictionPoints(summary.predictionPoints());
            score.setSideBetPoints(summary.sideBetPoints());
            score.setAbilityPoints(summary.abilityPoints());
            score.setFavorPoints(summary.favorPoints());
            score.setTotalPoints(summary.totalPoints());
            score.setPredictionCount(summary.predictionCount());
            score.setCorrectPredictions(summary.correctPredictions());
            score.setAbilityBreakdownJson(writeHouseAbilitySummaries(summary.abilitySummaries()));
            score.setScoredAt(now);
            scores.add(score);
        }
        houseScoreRepository.saveAll(scores);
    }

    private void sortPredictionSummaries(List<HousePredictionSummary> summaries) {
        summaries.sort(Comparator.comparingInt(HousePredictionSummary::totalPoints)
                .thenComparingInt(HousePredictionSummary::correctPredictions)
                .thenComparingInt(HousePredictionSummary::predictionCount)
                .reversed()
                .thenComparing(summary -> summary.house().displayName()));
    }

    private String writeHouseAbilitySummaries(List<HouseAbilitySummary> summaries) {
        try {
            return JsonMapperManager.basic().writeValueAsString(summaries);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to write replay house ability summary.", e);
        }
    }

    private List<HouseAbilitySummary> readHouseAbilitySummaries(String summariesJson) {
        if (summariesJson == null || summariesJson.isBlank()) return List.of();
        try {
            return JsonMapperManager.basic()
                    .readerForListOf(HouseAbilitySummary.class)
                    .readValue(summariesJson);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to read replay house ability summary.", e);
        }
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private EnumMap<CombatReplayHouse, HouseTotals> emptyTotalsByHouse() {
        EnumMap<CombatReplayHouse, HouseTotals> totalsByHouse = new EnumMap<>(CombatReplayHouse.class);
        for (CombatReplayHouse house : CombatReplayHouse.assignmentOrder()) {
            totalsByHouse.put(house, new HouseTotals());
        }
        return totalsByHouse;
    }

    public record HouseAbilitySummary(String label, int points) {}

    public record HouseFavorSummary(String label, int favor) {}

    public record HousePredictionSummary(
            CombatReplayHouse house,
            int predictionPoints,
            int sideBetPoints,
            int abilityPoints,
            int favorPoints,
            int predictionCount,
            int correctPredictions,
            List<HouseAbilitySummary> abilitySummaries,
            List<HouseFavorSummary> favorSummaries) {
        public int totalPoints() {
            return predictionPoints + sideBetPoints + abilityPoints;
        }
    }

    public record HouseLeaderboardSummary(
            CombatReplayHouse house,
            int totalPoints,
            int predictionCount,
            int correctPredictions,
            int memberCount,
            int abilityPoints) {}
}
