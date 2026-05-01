package ti4.contest.replay.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import ti4.contest.replay.core.CombatContestSettings;
import ti4.contest.replay.core.CombatReplaySelection;
import ti4.contest.replay.core.LazaxCombatSupport;
import ti4.contest.replay.entities.CombatCandidateEntity;
import ti4.contest.replay.entities.CombatObservationEntity;
import ti4.contest.replay.repository.CombatCandidateEventRepository;
import ti4.contest.replay.repository.CombatCandidateRepository;
import ti4.contest.replay.repository.CombatObservationRepository;

class CombatReplayServiceTest {

    @Test
    void promotionScorePrioritizesEndingTensionOverOpeningBalance() {
        CombatObservationEntity blowout = observation(
                10L, LocalDateTime.of(2026, 4, 23, 20, 22), "pbd22333", "307", 15, 20, 9, 9, 2.2, 2.6, 0.86);
        blowout.setAttackerFaction("sol");
        blowout.setDefenderFaction("mahact");
        CombatCandidateEntity blowoutCandidate = candidate("sol", "mahact");
        CombatReplayService.InitialCombatStats blowoutInitialStats = initialStats(
                blowout.getAttackerStrength(),
                blowout.getDefenderStrength(),
                blowout.getAttackerHp(),
                blowout.getDefenderHp());

        CombatObservationEntity squeaker = observation(
                19L, LocalDateTime.of(2026, 4, 23, 22, 41), "pbd19963f", "306", 22.5, 24.5, 16, 11, 4.5, 6.2, 0.77);
        squeaker.setAttackerFaction("naalu");
        squeaker.setDefenderFaction("muaat");
        CombatCandidateEntity squeakerCandidate = candidate("naalu", "muaat");
        CombatReplayService.InitialCombatStats squeakerInitialStats = initialStats(
                squeaker.getAttackerStrength(),
                squeaker.getDefenderStrength(),
                squeaker.getAttackerHp(),
                squeaker.getDefenderHp());

        double blowoutScore = CombatReplayService.computePromotionScore(
                blowoutCandidate,
                blowoutInitialStats,
                new LazaxCombatSupport.FleetStrength(3.0, 0.0, 0.0),
                new LazaxCombatSupport.FleetStrength(10.0, 6.0, 0.0),
                "mahact",
                3);
        double squeakerScore = CombatReplayService.computePromotionScore(
                squeakerCandidate,
                squeakerInitialStats,
                new LazaxCombatSupport.FleetStrength(0.0, 0.0, 0.0),
                new LazaxCombatSupport.FleetStrength(8.0, 3.0, 0.0),
                "muaat",
                4);

        assertEquals(2.7033, blowoutScore, 0.0001);
        assertEquals(4.1705, squeakerScore, 0.0001);
        assertTrue(squeakerScore > blowoutScore);
    }

    @Test
    void promotionScoreAddsHalfPointBonusWhenDefenderWins() {
        CombatCandidateEntity candidate = candidate("sol", "yin");
        CombatReplayService.InitialCombatStats initialStats = initialStats(12, 12, 8, 8);

        LazaxCombatSupport.FleetStrength attackerRemaining = new LazaxCombatSupport.FleetStrength(4.0, 2.0, 0.0);
        LazaxCombatSupport.FleetStrength defenderRemaining = new LazaxCombatSupport.FleetStrength(4.0, 2.0, 0.0);
        double attackerWinScore = CombatReplayService.computePromotionScore(
                candidate, initialStats, attackerRemaining, defenderRemaining, "sol", 2);
        double defenderWinScore = CombatReplayService.computePromotionScore(
                candidate, initialStats, attackerRemaining, defenderRemaining, "yin", 2);

        assertEquals(attackerWinScore + 0.5, defenderWinScore, 0.0001);
    }

    @Test
    void promotionScoreUsesInitialSnapshotStatsInsteadOfObservationStats() {
        CombatCandidateEntity candidate = candidate("letnev", "hacan");
        CombatReplayService.InitialCombatStats staleObservationStats = initialStats(13.0, 11.5, 11.0, 7.0);
        CombatReplayService.InitialCombatStats replaySnapshotStats = initialStats(4.5, 11.5, 4.0, 7.0);
        LazaxCombatSupport.FleetStrength attackerRemaining = new LazaxCombatSupport.FleetStrength(0.0, 0.0, 0.0);
        LazaxCombatSupport.FleetStrength defenderRemaining = new LazaxCombatSupport.FleetStrength(7.0, 5.0, 0.0);

        double staleScore = CombatReplayService.computePromotionScore(
                candidate, staleObservationStats, attackerRemaining, defenderRemaining, "hacan", 3);
        double snapshotScore = CombatReplayService.computePromotionScore(
                candidate, replaySnapshotStats, attackerRemaining, defenderRemaining, "hacan", 3);

        assertEquals(2.7074, staleScore, 0.0001);
        assertEquals(1.4888, snapshotScore, 0.0001);
        assertTrue(snapshotScore < staleScore);
    }

    @Test
    void selectionDebugViewExposesObservationWindowDetails() {
        CombatObservationRepository observationRepository = mock(CombatObservationRepository.class);
        CombatReplayService service = new CombatReplayService(
                new CombatContestSettings(),
                observationRepository,
                mock(CombatCandidateRepository.class),
                mock(CombatCandidateEventRepository.class),
                mock(CombatReplayEventAppender.class),
                mock(CombatReplaySideBetTriggerService.class));

        CombatObservationEntity first =
                observation(1L, LocalDateTime.of(2026, 4, 22, 10, 0), "pbd100", "19", 20, 40, 8, 8, 3, 3, 0.80);
        CombatObservationEntity second =
                observation(2L, LocalDateTime.of(2026, 4, 22, 10, 5), "pbd101", "20", 30, 30, 10, 10, 4, 4, 1.00);
        CombatObservationEntity third =
                observation(3L, LocalDateTime.of(2026, 4, 22, 10, 10), "pbd102", "21", 10, 20, 6, 6, 2, 2, 0.60);
        when(observationRepository.findByStartedAtGreaterThanEqualOrderByStartedAtAsc(any()))
                .thenReturn(List.of(first, second, third));

        service.refreshSelectionSnapshot();
        CombatReplaySelection.SelectionDebugView view = service.getSelectionDebugView();

        assertEquals(3, view.windowSize());
        assertEquals(0.80, view.averageFairnessRatio(), 0.0001);
        assertEquals(20.0, view.averageWeakerStrength(), 0.0001);
        assertEquals(1.0 / 9.0, view.jointScoreCutoff(), 0.0001);
        assertEquals(3, view.observations().size());

        CombatReplaySelection.SelectionObservationDebugView firstObservation =
                view.observations().getFirst();
        assertEquals(3L, firstObservation.observation().getId());
        assertTrue(firstObservation.eligibleAsCandidate());
        assertEquals("pbd102", firstObservation.observation().getGameName());
        assertEquals(1.0 / 9.0, firstObservation.jointScore(), 0.0001);

        CombatReplaySelection.SelectionObservationDebugView lastObservation =
                view.observations().get(2);
        assertEquals(1L, lastObservation.observation().getId());
        assertEquals("pbd100", lastObservation.observation().getGameName());
        assertEquals(20.0, lastObservation.weakerStrength(), 0.0001);
        assertEquals(2.0 / 3.0, lastObservation.fairnessPercentile(), 0.0001);
        assertEquals(2.0 / 3.0, lastObservation.weakerStrengthPercentile(), 0.0001);
        assertEquals(4.0 / 9.0, lastObservation.jointScore(), 0.0001);
        assertTrue(lastObservation.eligibleAsCandidate());
    }

    private CombatObservationEntity observation(
            Long id,
            LocalDateTime startedAt,
            String gameName,
            String tilePosition,
            double attackerStrength,
            double defenderStrength,
            double attackerHp,
            double defenderHp,
            double attackerExpectedHits,
            double defenderExpectedHits,
            double fairnessRatio) {
        CombatObservationEntity observation = new CombatObservationEntity();
        observation.setId(id);
        observation.setStartedAt(startedAt);
        observation.setGameName(gameName);
        observation.setTilePosition(tilePosition);
        observation.setAttackerFaction("hacan");
        observation.setDefenderFaction("sol");
        observation.setAttackerStrength(attackerStrength);
        observation.setDefenderStrength(defenderStrength);
        observation.setAttackerHp(attackerHp);
        observation.setDefenderHp(defenderHp);
        observation.setAttackerExpectedHits(attackerExpectedHits);
        observation.setDefenderExpectedHits(defenderExpectedHits);
        observation.setFairnessRatio(fairnessRatio);
        observation.setJointScore(0.0);
        return observation;
    }

    private CombatCandidateEntity candidate(String attackerFaction, String defenderFaction) {
        CombatCandidateEntity candidate = new CombatCandidateEntity();
        candidate.setAttackerFaction(attackerFaction);
        candidate.setDefenderFaction(defenderFaction);
        return candidate;
    }

    private CombatReplayService.InitialCombatStats initialStats(
            double attackerStrength, double defenderStrength, double attackerHp, double defenderHp) {
        return new CombatReplayService.InitialCombatStats(attackerStrength, defenderStrength, attackerHp, defenderHp);
    }
}
