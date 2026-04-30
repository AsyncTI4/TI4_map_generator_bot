package ti4.contest.replay.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import ti4.contest.replay.core.CombatCandidatePromotionStatus;
import ti4.contest.replay.core.CombatCandidateStatus;
import ti4.contest.replay.core.CombatContestSettings;
import ti4.contest.replay.repository.CombatCandidateEventRepository;
import ti4.contest.replay.repository.CombatCandidateRepository;
import ti4.contest.replay.repository.CombatObservationRepository;
import ti4.contest.replay.repository.CombatReplayContestRepository;

class CombatReplayContestLifecycleServiceTest {

    @Test
    void promoteBestCandidateIfDueDoesNothingWhenCandidatePromotionIsDisabled() {
        CombatCandidateRepository candidateRepository = mock(CombatCandidateRepository.class);
        CombatReplayContestRepository replayContestRepository = mock(CombatReplayContestRepository.class);
        CombatContestSettings settings = new CombatContestSettings();
        settings.getPromotion().setEnabled(false);
        CombatReplayContestLifecycleService service = service(settings, candidateRepository, replayContestRepository);

        service.promoteBestCandidateIfDue();

        verifyNoInteractions(candidateRepository, replayContestRepository);
    }

    @Test
    void promoteBestCandidateIfDueAllowsPreviousPromotionSlotEvenWhenPostWasLate() {
        CombatCandidateRepository candidateRepository = mock(CombatCandidateRepository.class);
        CombatReplayContestRepository replayContestRepository = mock(CombatReplayContestRepository.class);
        CombatContestSettings settings = new CombatContestSettings();
        CombatReplayContestLifecycleService service = service(settings, candidateRepository, replayContestRepository);
        service.setClock(fixedClock("2026-04-27T12:00:00"));
        LocalDateTime recentContestCutoff = LocalDateTime.parse("2026-04-27T11:00:00");

        when(replayContestRepository.countByPostedAtGreaterThanEqual(recentContestCutoff))
                .thenReturn(0L);
        when(replayContestRepository.countByPostedAtGreaterThanEqual(LocalDateTime.parse("2026-04-27T12:00:00")))
                .thenReturn(0L);
        when(candidateRepository.findResolvedPromotionCandidates(
                        CombatCandidateStatus.RESOLVED,
                        CombatCandidatePromotionStatus.PENDING,
                        LocalDateTime.parse("2026-04-27T00:00:00")))
                .thenReturn(List.of());

        service.promoteBestCandidateIfDue();

        verify(replayContestRepository).countByPostedAtGreaterThanEqual(recentContestCutoff);
        verify(candidateRepository)
                .findResolvedPromotionCandidates(
                        CombatCandidateStatus.RESOLVED,
                        CombatCandidatePromotionStatus.PENDING,
                        LocalDateTime.parse("2026-04-27T00:00:00"));
    }

    @Test
    void promoteBestCandidateIfDueBlocksWhenContestExistsInPreviousPromotionSlot() {
        CombatCandidateRepository candidateRepository = mock(CombatCandidateRepository.class);
        CombatReplayContestRepository replayContestRepository = mock(CombatReplayContestRepository.class);
        CombatContestSettings settings = new CombatContestSettings();
        CombatReplayContestLifecycleService service = service(settings, candidateRepository, replayContestRepository);
        service.setClock(fixedClock("2026-04-27T12:00:00"));
        LocalDateTime recentContestCutoff = LocalDateTime.parse("2026-04-27T11:00:00");

        when(replayContestRepository.countByPostedAtGreaterThanEqual(recentContestCutoff))
                .thenReturn(1L);

        service.promoteBestCandidateIfDue();

        verify(replayContestRepository).countByPostedAtGreaterThanEqual(recentContestCutoff);
        verifyNoInteractions(candidateRepository);
    }

    @Test
    void promoteBestCandidateIfDueRunsWhenCronIsLateForThePromotionSlot() {
        CombatCandidateRepository candidateRepository = mock(CombatCandidateRepository.class);
        CombatReplayContestRepository replayContestRepository = mock(CombatReplayContestRepository.class);
        CombatContestSettings settings = new CombatContestSettings();
        CombatReplayContestLifecycleService service = service(settings, candidateRepository, replayContestRepository);
        service.setClock(fixedClock("2026-04-27T12:45:00"));
        LocalDateTime recentContestCutoff = LocalDateTime.parse("2026-04-27T11:00:00");

        when(replayContestRepository.countByPostedAtGreaterThanEqual(recentContestCutoff))
                .thenReturn(0L);
        when(replayContestRepository.countByPostedAtGreaterThanEqual(LocalDateTime.parse("2026-04-27T12:00:00")))
                .thenReturn(0L);
        when(candidateRepository.findResolvedPromotionCandidates(
                        CombatCandidateStatus.RESOLVED,
                        CombatCandidatePromotionStatus.PENDING,
                        LocalDateTime.parse("2026-04-27T00:45:00")))
                .thenReturn(List.of());

        service.promoteBestCandidateIfDue();

        verify(replayContestRepository).countByPostedAtGreaterThanEqual(recentContestCutoff);
        verify(candidateRepository)
                .findResolvedPromotionCandidates(
                        CombatCandidateStatus.RESOLVED,
                        CombatCandidatePromotionStatus.PENDING,
                        LocalDateTime.parse("2026-04-27T00:45:00"));
    }

    @Test
    void forcePromoteCandidateRejectsWhenCandidatePromotionIsDisabled() {
        CombatCandidateRepository candidateRepository = mock(CombatCandidateRepository.class);
        CombatReplayContestRepository replayContestRepository = mock(CombatReplayContestRepository.class);
        CombatContestSettings settings = new CombatContestSettings();
        settings.getPromotion().setEnabled(false);
        CombatReplayContestLifecycleService service = service(settings, candidateRepository, replayContestRepository);

        CombatReplayContestLifecycleService.ForcePromoteResult result = service.forcePromoteCandidate(1L);

        assertFalse(result.promoted());
        assertEquals("Candidate-to-contest promotion is disabled.", result.reason());
        verifyNoInteractions(candidateRepository, replayContestRepository);
    }

    private CombatReplayContestLifecycleService service(
            CombatContestSettings settings,
            CombatCandidateRepository candidateRepository,
            CombatReplayContestRepository replayContestRepository) {
        return new CombatReplayContestLifecycleService(
                settings,
                candidateRepository,
                mock(CombatObservationRepository.class),
                replayContestRepository,
                mock(CombatCandidateEventRepository.class),
                mock(CombatReplayLeaderboardService.class),
                mock(CombatReplaySideBetService.class),
                mock(ReplayPayloadRenderer.class));
    }

    private Clock fixedClock(String localDateTime) {
        return Clock.fixed(
                LocalDateTime.parse(localDateTime)
                        .atZone(ZoneId.systemDefault())
                        .toInstant(),
                ZoneId.systemDefault());
    }
}
