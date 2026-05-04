package ti4.contest.replay.service;

import java.util.EnumMap;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import ti4.contest.replay.core.CombatContestSettings;
import ti4.contest.replay.core.CombatReplayHouse;
import ti4.contest.replay.entities.CombatReplayHouseScoreEntity;
import ti4.contest.replay.repository.CombatReplayHouseScoreRepository;

@Service
@Order(100)
@RequiredArgsConstructor
public class CombatReplayCustodianFavorScoringRule implements CombatReplayHouseScoringRule {

    private final CombatContestSettings settings;
    private final CombatReplayHouseScoreRepository houseScoreRepository;

    @Override
    public void apply(CombatReplayHouseScoringContext context) {
        EnumMap<CombatReplayHouse, Integer> seasonPointsByHouse = currentSeasonPointsByHouse(context);
        int leaderPoints = seasonPointsByHouse.values().stream()
                .mapToInt(Integer::intValue)
                .max()
                .orElse(0);

        for (CombatReplayHouse house : CombatReplayHouse.assignmentOrder()) {
            int pointsBehind = Math.max(0, leaderPoints - seasonPointsByHouse.getOrDefault(house, 0));
            int favor = combatFavorGain(pointsBehind);
            context.totals(house).favorPoints += favor;
            context.addFavorSummary(house, "the Custodians sealing this combat's ledger", favor, true);
        }
    }

    public int combatFavorGain(int pointsBehindLeader) {
        CombatContestSettings.HouseAbilities houseAbilities = settings.getHouseAbilities();
        int catchupBonus = Math.min(
                houseAbilities.getMaxCatchupFavorBonus(),
                (Math.max(0, pointsBehindLeader) / houseAbilities.getCatchupFavorPointsPerBonus())
                        * houseAbilities.getCatchupFavorBonusStep());
        return houseAbilities.getBaseCombatFavorGain() + catchupBonus;
    }

    private EnumMap<CombatReplayHouse, Integer> currentSeasonPointsByHouse(CombatReplayHouseScoringContext context) {
        EnumMap<CombatReplayHouse, Integer> pointsByHouse = new EnumMap<>(CombatReplayHouse.class);
        for (CombatReplayHouse house : CombatReplayHouse.assignmentOrder()) {
            CombatReplayHouseScoringContext.HouseTotals totals = context.totals(house);
            int currentContestPoints = totals == null ? 0 : totals.predictionPoints + totals.sideBetPoints;
            pointsByHouse.put(house, currentContestPoints + context.currentAbilityPoints(house));
        }

        Long currentContestId = context.contest().getId();
        for (CombatReplayHouseScoreEntity score : houseScoreRepository.findAll()) {
            if (currentContestId != null && currentContestId.equals(score.getContestId())) continue;
            pointsByHouse.computeIfPresent(
                    score.getHouse(), (house, points) -> points + safeInt(score.getTotalPoints()));
        }
        return pointsByHouse;
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }
}
