package ti4.contest.replay.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
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
import ti4.contest.replay.house.hacan.CombatReplayHacanMarketCompactService.MarkedSideBet;
import ti4.contest.replay.house.hacan.CombatReplayHacanTradeConvoysService;
import ti4.contest.replay.repository.CombatContestSideBetRepository;
import ti4.contest.replay.repository.CombatReplayHouseScoreRepository;
import ti4.contest.replay.service.CombatReplayPredictionScorer.LockedPrediction;
import ti4.contest.replay.service.CombatReplayPredictionScorer.ScoredPredictions;
import ti4.contest.replay.service.CombatReplaySideBetService.ResolvedSideBet;
import ti4.json.JsonMapperManager;

@Service
@RequiredArgsConstructor
public class CombatReplayHouseLedgerService {

    private static final long SEASON_OPENING_BALANCE_CONTEST_ID = 0L;
    private static final int WRONG_PREDICTION_PENALTY = -4;
    private static final int NAALU_GIFT_POINTS_PER_CORRECT_PREDICTION = 4;
    private static final int MENTAK_PILLAGE_POINTS_PER_OTHER_DELEGATION_MISS = 4;

    private final CombatContestSettings settings;
    private final CombatContestSideBetRepository sideBetRepository;
    private final CombatReplayHouseScoreRepository houseScoreRepository;
    private final CombatReplayHouseService houseService;
    private final CombatReplayHacanMarketCompactService hacanMarketCompactService;
    private final CombatReplayHacanTradeConvoysService hacanTradeConvoysService;

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

        applyPredictionLedger(
                replayContest,
                scoredPredictions,
                allPredictions,
                winningUserIds,
                housesByUser,
                totalsByHouse,
                abilitiesByHouse);
        applySideBetLedger(
                replayContest,
                sideBets,
                resolvedSideBets,
                housesByUser,
                totalsByHouse,
                abilitiesByHouse,
                favorsByHouse);
        applyHacanTradeConvoysFavorTransfer(replayContest, totalsByHouse, favorsByHouse);
        applyHacanTradeConvoysLedger(replayContest, totalsByHouse, abilitiesByHouse);

        EnumMap<CombatReplayHouse, Integer> currentAbilityPointsByHouse = currentAbilityPoints(abilitiesByHouse);
        EnumMap<CombatReplayHouse, Integer> currentContestPointsByHouse =
                currentContestPointsByHouse(totalsByHouse, currentAbilityPointsByHouse);
        for (Map.Entry<CombatReplayHouse, Integer> entry : combatFavorAwards(
                        replayContest.getId(), currentContestPointsByHouse)
                .entrySet()) {
            totalsByHouse.get(entry.getKey()).favorPoints += entry.getValue();
            addFavorSummary(
                    favorsByHouse,
                    entry.getKey(),
                    "the Custodians sealing this combat's ledger",
                    entry.getValue(),
                    true);
        }

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
            CombatReplayContestEntity replayContest,
            ScoredPredictions scoredPredictions,
            List<LockedPrediction> allPredictions,
            Set<String> winningUserIds,
            Map<String, CombatReplayHouse> housesByUser,
            EnumMap<CombatReplayHouse, HouseTotals> totalsByHouse,
            EnumMap<CombatReplayHouse, List<HouseAbilitySummary>> abilitiesByHouse) {
        int naaluGiftPoints = 0;
        int mentakPillagePoints = 0;
        for (LockedPrediction prediction : allPredictions) {
            CombatReplayHouse house = housesByUser.get(prediction.discordUserId());
            if (house == null) continue;
            boolean correct = winningUserIds.contains(prediction.discordUserId());
            HouseTotals totals = totalsByHouse.get(house);
            totals.predictionPoints += correct ? scoredPredictions.pointsAwarded() : WRONG_PREDICTION_PENALTY;
            totals.predictionCount++;
            if (correct) {
                totals.correctPredictions++;
                if (house == CombatReplayHouse.NAALU) naaluGiftPoints += NAALU_GIFT_POINTS_PER_CORRECT_PREDICTION;
            } else if (house != CombatReplayHouse.MENTAK) {
                mentakPillagePoints += MENTAK_PILLAGE_POINTS_PER_OTHER_DELEGATION_MISS;
            }
        }

        addAbilitySummary(abilitiesByHouse, CombatReplayHouse.NAALU, "Gift of Prophecy", naaluGiftPoints, true);
        addAbilitySummary(abilitiesByHouse, CombatReplayHouse.MENTAK, "Pillage", mentakPillagePoints, true);
    }

    private void applySideBetLedger(
            CombatReplayContestEntity replayContest,
            List<CombatContestSideBetEntity> sideBets,
            List<ResolvedSideBet> resolvedSideBets,
            Map<String, CombatReplayHouse> housesByUser,
            EnumMap<CombatReplayHouse, HouseTotals> totalsByHouse,
            EnumMap<CombatReplayHouse, List<HouseAbilitySummary>> abilitiesByHouse,
            EnumMap<CombatReplayHouse, List<HouseFavorSummary>> favorsByHouse) {
        int sideBetCost = settings.getSideBets().getCostPoints();
        int hacanInsiderTradingPoints = 0;
        for (CombatContestSideBetEntity sideBet : sideBets) {
            CombatReplayHouse house = housesByUser.get(sideBet.getDiscordUserId());
            if (house == null) continue;
            totalsByHouse.get(house).sideBetPoints -= sideBetCost;
            if (house == CombatReplayHouse.HACAN) hacanInsiderTradingPoints += sideBetCost;
        }

        int favorOnHit = hacanMarketCompactService.favorOnHit();
        Set<MarkedSideBet> markedSideBetHits = new HashSet<>();
        for (ResolvedSideBet sideBet : resolvedSideBets == null ? List.<ResolvedSideBet>of() : resolvedSideBets) {
            CombatReplayHouse house = housesByUser.get(sideBet.discordUserId());
            if (house == null) continue;
            totalsByHouse.get(house).sideBetPoints += sideBet.profitPoints();
            if (favorOnHit > 0
                    && hacanMarketCompactService.isMarked(
                            replayContest.getId(), sideBet.betType(), sideBet.targetFaction())) {
                MarkedSideBet hit = new MarkedSideBet(sideBet.betType(), normalizeFaction(sideBet.targetFaction()));
                if (markedSideBetHits.add(hit)) {
                    totalsByHouse.get(CombatReplayHouse.HACAN).favorPoints += favorOnHit;
                    addFavorSummary(favorsByHouse, CombatReplayHouse.HACAN, "Market Compact hits", favorOnHit, false);
                }
            }
        }

        addAbilitySummary(abilitiesByHouse, CombatReplayHouse.HACAN, "Insider Trading", hacanInsiderTradingPoints);
        addAbilitySummary(
                abilitiesByHouse,
                CombatReplayHouse.HACAN,
                "Market Compact",
                hacanMarketCompactService.marketMakerPoints(replayContest.getId(), sideBets));
    }

    private void applyHacanTradeConvoysLedger(
            CombatReplayContestEntity replayContest,
            EnumMap<CombatReplayHouse, HouseTotals> totalsByHouse,
            EnumMap<CombatReplayHouse, List<HouseAbilitySummary>> abilitiesByHouse) {
        CombatReplayHacanTradeConvoysService.TradeConvoys tradeConvoys =
                hacanTradeConvoysService.tradeConvoysForNextCombat(replayContest.getId());
        if (!tradeConvoys.active()) return;

        int targetEarnedPoints = earnedPointsForHouse(
                totalsByHouse.get(tradeConvoys.targetHouse()), abilitiesByHouse.get(tradeConvoys.targetHouse()));
        int bonusPoints = CombatReplayHacanTradeConvoysService.tradeConvoysBonusPoints(
                targetEarnedPoints, tradeConvoys.bonusPercent());
        addAbilitySummary(abilitiesByHouse, CombatReplayHouse.HACAN, "Hacan Trade Convoys", bonusPoints);
    }

    private void applyHacanTradeConvoysFavorTransfer(
            CombatReplayContestEntity replayContest,
            EnumMap<CombatReplayHouse, HouseTotals> totalsByHouse,
            EnumMap<CombatReplayHouse, List<HouseFavorSummary>> favorsByHouse) {
        CombatReplayHacanTradeConvoysService.TradeConvoys tradeConvoys =
                hacanTradeConvoysService.tradeConvoysForContest(replayContest.getId());
        if (!tradeConvoys.active()) return;
        totalsByHouse.get(tradeConvoys.targetHouse()).favorPoints += tradeConvoys.favorCost();
        addFavorSummary(favorsByHouse, tradeConvoys.targetHouse(), "Trade Convoys", tradeConvoys.favorCost(), false);
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

    private EnumMap<CombatReplayHouse, Integer> currentContestPointsByHouse(
            EnumMap<CombatReplayHouse, HouseTotals> totalsByHouse,
            EnumMap<CombatReplayHouse, Integer> currentAbilityPointsByHouse) {
        EnumMap<CombatReplayHouse, Integer> pointsByHouse = new EnumMap<>(CombatReplayHouse.class);
        for (CombatReplayHouse house : CombatReplayHouse.assignmentOrder()) {
            HouseTotals totals = totalsByHouse.get(house);
            int points = totals == null ? 0 : totals.predictionPoints + totals.sideBetPoints;
            pointsByHouse.put(house, points + currentAbilityPointsByHouse.getOrDefault(house, 0));
        }
        return pointsByHouse;
    }

    private int earnedPointsForHouse(HouseTotals totals, List<HouseAbilitySummary> abilitySummaries) {
        if (totals == null) return 0;
        int abilityPoints = abilitySummaries == null
                ? 0
                : abilitySummaries.stream()
                        .mapToInt(HouseAbilitySummary::points)
                        .sum();
        return totals.predictionPoints + totals.sideBetPoints + abilityPoints;
    }

    private void addAbilitySummary(
            EnumMap<CombatReplayHouse, List<HouseAbilitySummary>> abilitiesByHouse,
            CombatReplayHouse house,
            String label,
            int points) {
        addAbilitySummary(abilitiesByHouse, house, label, points, false);
    }

    private void addAbilitySummary(
            EnumMap<CombatReplayHouse, List<HouseAbilitySummary>> abilitiesByHouse,
            CombatReplayHouse house,
            String label,
            int points,
            boolean includeZero) {
        if (house == null || (!includeZero && points <= 0)) return;
        abilitiesByHouse.get(house).add(new HouseAbilitySummary(label, points));
    }

    private void addFavorSummary(
            EnumMap<CombatReplayHouse, List<HouseFavorSummary>> favorsByHouse,
            CombatReplayHouse house,
            String label,
            int favor,
            boolean includeZero) {
        if (house == null || (!includeZero && favor <= 0)) return;
        List<HouseFavorSummary> summaries = favorsByHouse.get(house);
        for (int index = 0; index < summaries.size(); index++) {
            HouseFavorSummary summary = summaries.get(index);
            if (summary.label().equals(label)) {
                summaries.set(index, new HouseFavorSummary(label, summary.favor() + favor));
                return;
            }
        }
        summaries.add(new HouseFavorSummary(label, favor));
    }

    private EnumMap<CombatReplayHouse, Integer> combatFavorAwards(
            Long currentContestId, EnumMap<CombatReplayHouse, Integer> currentAbilityPointsByHouse) {
        EnumMap<CombatReplayHouse, Integer> seasonPointsByHouse =
                currentSeasonPointsByHouse(currentContestId, currentAbilityPointsByHouse);
        int leaderPoints = seasonPointsByHouse.values().stream()
                .mapToInt(Integer::intValue)
                .max()
                .orElse(0);

        EnumMap<CombatReplayHouse, Integer> favorAwards = new EnumMap<>(CombatReplayHouse.class);
        for (CombatReplayHouse house : CombatReplayHouse.assignmentOrder()) {
            int pointsBehind = Math.max(0, leaderPoints - seasonPointsByHouse.getOrDefault(house, 0));
            favorAwards.put(house, combatFavorGain(pointsBehind));
        }
        return favorAwards;
    }

    public int combatFavorGain(int pointsBehindLeader) {
        CombatContestSettings.HouseAbilities houseAbilities = settings.getHouseAbilities();
        int catchupBonus = Math.min(
                houseAbilities.getMaxCatchupFavorBonus(),
                (Math.max(0, pointsBehindLeader) / houseAbilities.getCatchupFavorPointsPerBonus())
                        * houseAbilities.getCatchupFavorBonusStep());
        return houseAbilities.getBaseCombatFavorGain() + catchupBonus;
    }

    private EnumMap<CombatReplayHouse, Integer> currentSeasonPointsByHouse(
            Long currentContestId, EnumMap<CombatReplayHouse, Integer> currentContestPointsByHouse) {
        EnumMap<CombatReplayHouse, Integer> pointsByHouse = new EnumMap<>(CombatReplayHouse.class);
        for (CombatReplayHouse house : CombatReplayHouse.assignmentOrder()) {
            pointsByHouse.put(house, currentContestPointsByHouse.getOrDefault(house, 0));
        }

        for (CombatReplayHouseScoreEntity score : houseScoreRepository.findAll()) {
            if (currentContestId != null && currentContestId.equals(score.getContestId())) continue;
            pointsByHouse.computeIfPresent(
                    score.getHouse(), (house, points) -> points + safeInt(score.getTotalPoints()));
        }
        return pointsByHouse;
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

    private String normalizeFaction(String faction) {
        return faction == null ? "" : faction.trim().toLowerCase(Locale.ROOT);
    }

    private EnumMap<CombatReplayHouse, HouseTotals> emptyTotalsByHouse() {
        EnumMap<CombatReplayHouse, HouseTotals> totalsByHouse = new EnumMap<>(CombatReplayHouse.class);
        for (CombatReplayHouse house : CombatReplayHouse.assignmentOrder()) {
            totalsByHouse.put(house, new HouseTotals());
        }
        return totalsByHouse;
    }

    private static class HouseTotals {
        private int predictionPoints;
        private int sideBetPoints;
        private int favorPoints;
        private int predictionCount;
        private int correctPredictions;
        private int memberCount;
        private int abilityPoints;
        private int totalPoints;
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
