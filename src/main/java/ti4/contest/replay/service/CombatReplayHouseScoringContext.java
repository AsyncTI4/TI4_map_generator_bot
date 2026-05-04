package ti4.contest.replay.service;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import ti4.contest.replay.core.CombatReplayHouse;
import ti4.contest.replay.entities.CombatContestSideBetEntity;
import ti4.contest.replay.entities.CombatReplayContestEntity;
import ti4.contest.replay.service.CombatReplayHouseLedgerService.HouseAbilitySummary;
import ti4.contest.replay.service.CombatReplayHouseLedgerService.HouseFavorSummary;
import ti4.contest.replay.service.CombatReplayPredictionScorer.LockedPrediction;
import ti4.contest.replay.service.CombatReplaySideBetService.ResolvedSideBet;

public class CombatReplayHouseScoringContext {

    private final CombatReplayContestEntity contest;
    private final List<LockedPrediction> allPredictions;
    private final List<HousePrediction> predictions;
    private final Set<String> winningUserIds;
    private final List<CombatContestSideBetEntity> sideBets;
    private final List<ResolvedSideBet> resolvedSideBets;
    private final Map<String, CombatReplayHouse> housesByUser;
    private final EnumMap<CombatReplayHouse, HouseTotals> totalsByHouse;
    private final EnumMap<CombatReplayHouse, List<HouseAbilitySummary>> abilitiesByHouse;
    private final EnumMap<CombatReplayHouse, List<HouseFavorSummary>> favorsByHouse;

    public CombatReplayHouseScoringContext(
            CombatReplayContestEntity contest,
            List<LockedPrediction> allPredictions,
            Set<String> winningUserIds,
            List<CombatContestSideBetEntity> sideBets,
            List<ResolvedSideBet> resolvedSideBets,
            Map<String, CombatReplayHouse> housesByUser,
            EnumMap<CombatReplayHouse, HouseTotals> totalsByHouse,
            EnumMap<CombatReplayHouse, List<HouseAbilitySummary>> abilitiesByHouse,
            EnumMap<CombatReplayHouse, List<HouseFavorSummary>> favorsByHouse) {
        this.contest = contest;
        this.allPredictions = allPredictions == null ? List.of() : allPredictions;
        this.predictions = this.allPredictions.stream()
                .map(prediction -> new HousePrediction(
                        prediction.discordUserId(),
                        prediction.discordUserName(),
                        winningUserIds != null && winningUserIds.contains(prediction.discordUserId())))
                .toList();
        this.winningUserIds = winningUserIds == null ? Set.of() : winningUserIds;
        this.sideBets = sideBets == null ? List.of() : sideBets;
        this.resolvedSideBets = resolvedSideBets == null ? List.of() : resolvedSideBets;
        this.housesByUser = housesByUser == null ? Map.of() : housesByUser;
        this.totalsByHouse = totalsByHouse;
        this.abilitiesByHouse = abilitiesByHouse;
        this.favorsByHouse = favorsByHouse;
    }

    public CombatReplayContestEntity contest() {
        return contest;
    }

    public List<LockedPrediction> allPredictions() {
        return allPredictions;
    }

    public List<HousePrediction> predictions() {
        return predictions;
    }

    public boolean isWinningPrediction(LockedPrediction prediction) {
        return prediction != null && winningUserIds.contains(prediction.discordUserId());
    }

    public List<CombatContestSideBetEntity> sideBets() {
        return sideBets;
    }

    public List<ResolvedSideBet> resolvedSideBets() {
        return resolvedSideBets;
    }

    public CombatReplayHouse houseForUser(String discordUserId) {
        return housesByUser.get(discordUserId);
    }

    public HouseTotals totals(CombatReplayHouse house) {
        return totalsByHouse.get(house);
    }

    public int currentAbilityPoints(CombatReplayHouse house) {
        return abilitiesByHouse.getOrDefault(house, List.of()).stream()
                .mapToInt(HouseAbilitySummary::points)
                .sum();
    }

    public void addAbilitySummary(CombatReplayHouse house, String label, int points) {
        addAbilitySummary(house, label, points, false);
    }

    public void addAbilitySummary(CombatReplayHouse house, String label, int points, boolean includeZero) {
        if (house == null || (!includeZero && points <= 0)) return;
        abilitiesByHouse.get(house).add(new HouseAbilitySummary(label, points));
    }

    public void addFavorSummary(CombatReplayHouse house, String label, int favor, boolean includeZero) {
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

    public int earnedPointsForHouse(CombatReplayHouse house) {
        HouseTotals totals = totals(house);
        if (totals == null) return 0;
        return totals.predictionPoints + totals.sideBetPoints + currentAbilityPoints(house);
    }

    public String normalizeFaction(String faction) {
        return faction == null ? "" : faction.trim().toLowerCase(Locale.ROOT);
    }

    public static class HouseTotals {
        public int predictionPoints;
        public int sideBetPoints;
        public int favorPoints;
        public int predictionCount;
        public int correctPredictions;
        public int memberCount;
        public int abilityPoints;
        public int totalPoints;
    }

    public record HousePrediction(String discordUserId, String discordUserName, boolean correct) {}
}
