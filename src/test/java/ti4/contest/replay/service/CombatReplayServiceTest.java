package ti4.contest.replay.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import ti4.contest.replay.entities.CombatObservationEntity;
import ti4.contest.replay.repository.CombatCandidateEventRepository;
import ti4.contest.replay.repository.CombatCandidateRepository;
import ti4.contest.replay.repository.CombatObservationRepository;
import ti4.spring.service.contest.CombatContestType;

class CombatReplayServiceTest {

    @Test
    void selectionDebugViewExposesObservationWindowDetails() {
        CombatObservationRepository observationRepository = mock(CombatObservationRepository.class);
        CombatReplayService service = new CombatReplayService(
                observationRepository,
                mock(CombatCandidateRepository.class),
                mock(CombatCandidateEventRepository.class),
                mock(CombatReplayEventAppender.class));

        CombatObservationEntity first = observation(
                1L, LocalDateTime.of(2026, 4, 22, 10, 0), "pbd100", "19", 20, 40, 8, 8, 3, 3, 0.80, true, 11L);
        CombatObservationEntity second = observation(
                2L, LocalDateTime.of(2026, 4, 22, 10, 5), "pbd101", "20", 30, 30, 10, 10, 4, 4, 1.00, true, 12L);
        CombatObservationEntity third = observation(
                3L, LocalDateTime.of(2026, 4, 22, 10, 10), "pbd102", "21", 10, 20, 6, 6, 2, 2, 0.60, false, null);
        when(observationRepository.findByStartedAtGreaterThanEqualOrderByStartedAtAsc(any()))
                .thenReturn(List.of(first, second, third));

        service.refreshSelectionSnapshot();
        CombatReplayService.SelectionDebugView view = service.getSelectionDebugView();

        assertEquals(3, view.windowSize());
        assertEquals(0.80, view.averageFairnessRatio(), 0.0001);
        assertEquals(20.0, view.averageWeakerStrength(), 0.0001);
        assertEquals(1.0 / 9.0, view.jointScoreCutoff(), 0.0001);
        assertEquals(3, view.observations().size());

        CombatReplayService.SelectionObservationDebugView firstObservation =
                view.observations().getFirst();
        assertEquals(1L, firstObservation.observation().getId());
        assertEquals("pbd100", firstObservation.observation().getGameName());
        assertEquals(20.0, firstObservation.weakerStrength(), 0.0001);
        assertEquals(2.0 / 3.0, firstObservation.fairnessPercentile(), 0.0001);
        assertEquals(2.0 / 3.0, firstObservation.weakerStrengthPercentile(), 0.0001);
        assertEquals(4.0 / 9.0, firstObservation.jointScore(), 0.0001);
        assertTrue(firstObservation.eligibleAsCandidate());
        assertEquals(11L, firstObservation.observation().getCandidateId());

        CombatReplayService.SelectionObservationDebugView lastObservation =
                view.observations().get(2);
        assertFalse(lastObservation.eligibleAsCandidate());
        assertEquals("pbd102", lastObservation.observation().getGameName());
        assertEquals(1.0 / 9.0, lastObservation.jointScore(), 0.0001);
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
            double fairnessRatio,
            boolean eligibleAsCandidate,
            Long candidateId) {
        CombatObservationEntity observation = new CombatObservationEntity();
        observation.setId(id);
        observation.setStartedAt(startedAt);
        observation.setGameName(gameName);
        observation.setTilePosition(tilePosition);
        observation.setCombatType(CombatContestType.SPACE);
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
        observation.setEligibleAsCandidate(eligibleAsCandidate);
        observation.setCandidateId(candidateId);
        return observation;
    }
}
