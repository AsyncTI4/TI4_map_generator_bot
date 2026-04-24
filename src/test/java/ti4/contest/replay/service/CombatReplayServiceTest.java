package ti4.contest.replay.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import ti4.contest.replay.core.CombatCandidateStatus;
import ti4.contest.replay.core.CombatContestSettings;
import ti4.contest.replay.core.LazaxCombatSupport;
import ti4.contest.replay.entities.CombatCandidateEntity;
import ti4.contest.replay.entities.CombatCandidateEventEntity;
import ti4.contest.replay.entities.CombatObservationEntity;
import ti4.contest.replay.repository.CombatCandidateEventRepository;
import ti4.contest.replay.repository.CombatCandidateRepository;
import ti4.contest.replay.repository.CombatObservationRepository;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Constants;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitType;
import ti4.spring.service.contest.CombatContestType;
import ti4.testUtils.BaseTi4Test;

class CombatReplayServiceTest extends BaseTi4Test {

    @Test
    void promotionScorePrioritizesEndingTensionOverOpeningBalance() {
        CombatObservationEntity blowout = observation(
                10L, LocalDateTime.of(2026, 4, 23, 20, 22), "pbd22333", "307", 15, 20, 9, 9, 2.2, 2.6, 0.86, true, 10L);
        blowout.setAttackerFaction("sol");
        blowout.setDefenderFaction("mahact");

        CombatObservationEntity squeaker = observation(
                19L,
                LocalDateTime.of(2026, 4, 23, 22, 41),
                "pbd19963f",
                "306",
                22.5,
                24.5,
                16,
                11,
                4.5,
                6.2,
                0.77,
                true,
                19L);
        squeaker.setAttackerFaction("naalu");
        squeaker.setDefenderFaction("muaat");

        double blowoutScore = CombatReplayService.computePromotionScore(
                blowout,
                new LazaxCombatSupport.FleetStrength(3.0, 0.0, 0.0),
                new LazaxCombatSupport.FleetStrength(10.0, 6.0, 0.0),
                "mahact",
                3);
        double squeakerScore = CombatReplayService.computePromotionScore(
                squeaker,
                new LazaxCombatSupport.FleetStrength(0.0, 0.0, 0.0),
                new LazaxCombatSupport.FleetStrength(8.0, 3.0, 0.0),
                "muaat",
                4);

        assertEquals(2.2033, blowoutScore, 0.0001);
        assertEquals(3.6705, squeakerScore, 0.0001);
        assertTrue(squeakerScore > blowoutScore);
    }

    @Test
    void selectionDebugViewExposesObservationWindowDetails() {
        CombatObservationRepository observationRepository = mock(CombatObservationRepository.class);
        CombatReplayService service = new CombatReplayService(
                new CombatContestSettings(),
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
        assertEquals(3L, firstObservation.observation().getId());
        assertFalse(firstObservation.eligibleAsCandidate());
        assertEquals("pbd102", firstObservation.observation().getGameName());
        assertEquals(1.0 / 9.0, firstObservation.jointScore(), 0.0001);

        CombatReplayService.SelectionObservationDebugView lastObservation =
                view.observations().get(2);
        assertEquals(1L, lastObservation.observation().getId());
        assertEquals("pbd100", lastObservation.observation().getGameName());
        assertEquals(20.0, lastObservation.weakerStrength(), 0.0001);
        assertEquals(2.0 / 3.0, lastObservation.fairnessPercentile(), 0.0001);
        assertEquals(2.0 / 3.0, lastObservation.weakerStrengthPercentile(), 0.0001);
        assertEquals(4.0 / 9.0, lastObservation.jointScore(), 0.0001);
        assertTrue(lastObservation.eligibleAsCandidate());
        assertEquals(11L, lastObservation.observation().getCandidateId());
    }

    @Test
    void refreshPromotionOpeningStateUpdatesStoredSnapshotAndStartSummary() {
        CombatObservationRepository observationRepository = mock(CombatObservationRepository.class);
        CombatCandidateRepository candidateRepository = mock(CombatCandidateRepository.class);
        CombatCandidateEventRepository candidateEventRepository = mock(CombatCandidateEventRepository.class);
        CombatReplayService service = new CombatReplayService(
                new CombatContestSettings(),
                observationRepository,
                candidateRepository,
                candidateEventRepository,
                mock(CombatReplayEventAppender.class));

        Game game = createCombatGame();
        CombatCandidateEntity candidate = new CombatCandidateEntity();
        candidate.setId(11L);
        candidate.setStatus(CombatCandidateStatus.TRACKING);
        candidate.setTilePosition("19");
        candidate.setAttackerFaction("hacan");
        candidate.setDefenderFaction("sol");

        CombatCandidateEventEntity startEvent = new CombatCandidateEventEntity();
        startEvent.setCandidateId(11L);
        startEvent.setSequenceNumber(1);
        startEvent.setSummaryText("stale");

        when(candidateRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(candidate));
        when(candidateEventRepository.findByCandidateIdAndSequenceNumber(11L, 1))
                .thenReturn(Optional.of(startEvent));

        service.refreshPromotionOpeningState(game, candidate);

        verify(candidateRepository).save(eq(candidate));
        verify(candidateEventRepository).save(eq(startEvent));
        assertNotNull(candidate.getInitialRenderSnapshotJson());
        assertTrue(!candidate.getInitialRenderSnapshotJson().isBlank());
        assertTrue(startEvent.getSummaryText().contains("## Lazax Candidate Recorded"));
        assertTrue(startEvent.getSummaryText().contains("Open Game"));
        assertTrue(startEvent.getSummaryText().contains("Hacan"));
        assertTrue(startEvent.getSummaryText().contains("Sol"));
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

    private Game createCombatGame() {
        Game game = new Game();
        game.newGameSetup();
        game.setName("pbd-test");
        game.setActivePlayerID("1");

        Player attacker = new Player("1", "Alice", game);
        attacker.setColor("yellow");
        attacker.setFaction(game, "hacan");
        attacker.addOwnedUnitByID("cruiser");
        attacker.addOwnedUnitByID("destroyer");
        game.getPlayers().put(attacker.getUserID(), attacker);

        Player defender = new Player("2", "Bob", game);
        defender.setColor("blue");
        defender.setFaction(game, "sol");
        defender.addOwnedUnitByID("cruiser");
        defender.addOwnedUnitByID("fighter");
        game.getPlayers().put(defender.getUserID(), defender);

        Tile tile = new Tile("18", "19");
        game.setTile(tile);
        tile.addUnit(Constants.SPACE, Units.getUnitKey(UnitType.Cruiser, attacker.getColorID()), 1);
        tile.addUnit(Constants.SPACE, Units.getUnitKey(UnitType.Destroyer, attacker.getColorID()), 1);
        tile.addUnit(Constants.SPACE, Units.getUnitKey(UnitType.Cruiser, defender.getColorID()), 1);
        tile.addUnit(Constants.SPACE, Units.getUnitKey(UnitType.Fighter, defender.getColorID()), 2);
        return game;
    }
}
