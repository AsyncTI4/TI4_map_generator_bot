package ti4.contest.replay.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import ti4.contest.replay.core.CombatContestSettings;
import ti4.contest.replay.core.CombatReplayHouse;
import ti4.contest.replay.entities.CombatReplayHouseEntity;
import ti4.contest.replay.entities.CombatReplayHouseScoreEntity;
import ti4.contest.replay.house.hacan.CombatReplayHacanMarketCompactService;
import ti4.contest.replay.house.hacan.CombatReplayHacanTradeConvoysService;
import ti4.contest.replay.repository.CombatContestSideBetRepository;
import ti4.contest.replay.repository.CombatReplayHouseScoreRepository;
import ti4.contest.replay.service.CombatReplayHouseLedgerService.HouseLeaderboardSummary;

class CombatReplayHouseLedgerServiceFavorTest {

    private final CombatContestSettings settings = new CombatContestSettings();
    private final CombatReplayHouseScoreRepository houseScoreRepository = mock(CombatReplayHouseScoreRepository.class);
    private final CombatReplayHouseService houseService = mock(CombatReplayHouseService.class);
    private final CombatReplayCustodianFavorScoringRule custodianFavorScoringRule =
            new CombatReplayCustodianFavorScoringRule(settings, houseScoreRepository);
    private final CombatReplayHouseLedgerService service = new CombatReplayHouseLedgerService(
            settings,
            mock(CombatContestSideBetRepository.class),
            houseScoreRepository,
            houseService,
            mock(CombatReplayHacanMarketCompactService.class),
            mock(CombatReplayHacanTradeConvoysService.class),
            custodianFavorScoringRule,
            List.of(custodianFavorScoringRule));

    @Test
    void defaultCombatFavorGainDoesNotApplyCatchupBonus() {
        assertEquals(10, service.combatFavorGain(0));
        assertEquals(10, service.combatFavorGain(500));
    }

    @Test
    void hacanGetsHigherBaseCombatFavorGain() {
        assertEquals(10, custodianFavorScoringRule.combatFavorGain(CombatReplayHouse.NAALU, 0));
        assertEquals(20, custodianFavorScoringRule.combatFavorGain(CombatReplayHouse.HACAN, 0));
    }

    @Test
    void catchupFavorCanBeConfiguredBackOn() {
        settings.getHouseAbilities().setCatchupFavorBonusStep(10);
        settings.getHouseAbilities().setMaxCatchupFavorBonus(30);

        assertEquals(10, service.combatFavorGain(99));
        assertEquals(20, service.combatFavorGain(100));
        assertEquals(40, service.combatFavorGain(500));
    }

    @Test
    void seasonOpeningBalancesSeedEveryDelegationAtConfiguredPoints() {
        settings.getHouseAbilities().setInitialHousePoints(1000);
        when(houseScoreRepository.findByContestId(0L)).thenReturn(List.of());

        service.ensureSeasonOpeningBalances();

        ArgumentCaptor<List<CombatReplayHouseScoreEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(houseScoreRepository).saveAllAndFlush(captor.capture());
        List<CombatReplayHouseScoreEntity> scores = captor.getValue();

        assertEquals(3, scores.size());
        for (CombatReplayHouseScoreEntity score : scores) {
            assertEquals(0L, score.getContestId());
            assertEquals(1000, score.getTotalPoints());
            assertEquals(0, score.getPredictionPoints());
            assertEquals(0, score.getSideBetPoints());
            assertEquals(0, score.getAbilityPoints());
        }
    }

    @Test
    void delegationLeaderboardUsesDelegationScoreLedgerNotMemberPointTotals() {
        when(houseService.allHouseAssignments())
                .thenReturn(List.of(
                        houseAssignment("1", CombatReplayHouse.NAALU),
                        houseAssignment("2", CombatReplayHouse.NAALU),
                        houseAssignment("3", CombatReplayHouse.HACAN)));
        when(houseScoreRepository.findAll())
                .thenReturn(List.of(
                        houseScore(CombatReplayHouse.NAALU, 0, 0, 0, 1000, 0, 0),
                        houseScore(CombatReplayHouse.HACAN, 0, 0, 0, 1000, 0, 0),
                        houseScore(CombatReplayHouse.MENTAK, 0, 0, 0, 1000, 0, 0),
                        houseScore(CombatReplayHouse.NAALU, 12, -1, 4, 15, 3, 2)));

        List<HouseLeaderboardSummary> summaries = service.leaderboardSummaries();

        HouseLeaderboardSummary naalu = summaries.stream()
                .filter(summary -> summary.house() == CombatReplayHouse.NAALU)
                .findFirst()
                .orElseThrow();
        assertEquals(1015, naalu.totalPoints());
        assertEquals(2, naalu.memberCount());
        assertEquals(3, naalu.predictionCount());
        assertEquals(2, naalu.correctPredictions());
    }

    private CombatReplayHouseEntity houseAssignment(String userId, CombatReplayHouse house) {
        CombatReplayHouseEntity assignment = new CombatReplayHouseEntity();
        assignment.setDiscordUserId(userId);
        assignment.setDiscordUserName(userId);
        assignment.setHouse(house);
        return assignment;
    }

    private CombatReplayHouseScoreEntity houseScore(
            CombatReplayHouse house,
            int predictionPoints,
            int sideBetPoints,
            int abilityPoints,
            int totalPoints,
            int predictionCount,
            int correctPredictions) {
        CombatReplayHouseScoreEntity score = new CombatReplayHouseScoreEntity();
        score.setHouse(house);
        score.setPredictionPoints(predictionPoints);
        score.setSideBetPoints(sideBetPoints);
        score.setAbilityPoints(abilityPoints);
        score.setTotalPoints(totalPoints);
        score.setPredictionCount(predictionCount);
        score.setCorrectPredictions(correctPredictions);
        return score;
    }
}
