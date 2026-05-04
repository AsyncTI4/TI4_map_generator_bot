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
import ti4.contest.replay.core.CombatContestReplayStatus;
import ti4.contest.replay.core.CombatContestSettings;
import ti4.contest.replay.entities.CombatCandidateEntity;
import ti4.contest.replay.entities.CombatReplayContestEntity;
import ti4.contest.replay.house.mentak.CombatReplayMentakAbilityService;
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
    void promoteBestCandidateIfDueAllowsFiveMinuteCooldownGrace() {
        CombatCandidateRepository candidateRepository = mock(CombatCandidateRepository.class);
        CombatReplayContestRepository replayContestRepository = mock(CombatReplayContestRepository.class);
        CombatContestSettings settings = new CombatContestSettings();
        settings.setHousesEnabled(false);
        settings.getRuntime().setDevMode(false);
        CombatReplayContestLifecycleService service = service(settings, candidateRepository, replayContestRepository);
        service.setClock(fixedClock("2026-04-27T12:00:00"));
        LocalDateTime recentContestCutoff = LocalDateTime.parse("2026-04-27T10:05:00");

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
    void promoteBestCandidateIfDueBlocksWhenContestExistsInsideCooldownGrace() {
        CombatCandidateRepository candidateRepository = mock(CombatCandidateRepository.class);
        CombatReplayContestRepository replayContestRepository = mock(CombatReplayContestRepository.class);
        CombatContestSettings settings = new CombatContestSettings();
        settings.setHousesEnabled(false);
        settings.getRuntime().setDevMode(false);
        CombatReplayContestLifecycleService service = service(settings, candidateRepository, replayContestRepository);
        service.setClock(fixedClock("2026-04-27T12:00:00"));
        LocalDateTime recentContestCutoff = LocalDateTime.parse("2026-04-27T10:05:00");

        when(replayContestRepository.countByPostedAtGreaterThanEqual(recentContestCutoff))
                .thenReturn(1L);

        service.promoteBestCandidateIfDue();

        verify(replayContestRepository).countByPostedAtGreaterThanEqual(recentContestCutoff);
        verifyNoInteractions(candidateRepository);
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

    @Test
    void forcePromoteCandidateDoesNotPubliclyPromoteBeforeMentakPreviewWindowExpires() {
        CombatCandidateRepository candidateRepository = mock(CombatCandidateRepository.class);
        CombatReplayContestRepository replayContestRepository = mock(CombatReplayContestRepository.class);
        CombatContestSettings settings = new CombatContestSettings();
        settings.getPromotion().setEnabled(true);
        settings.setHousesEnabled(true);
        settings.getHouseAbilities().getMentak().setPreviewLeadSeconds(900);
        CombatReplayContestLifecycleService service = service(settings, candidateRepository, replayContestRepository);
        service.setClock(fixedClock("2026-04-27T12:14:59"));
        CombatCandidateEntity candidate = previewedCandidate(LocalDateTime.parse("2026-04-27T12:00:00"));
        CombatReplayContestEntity contest = previewContest();

        when(candidateRepository.findById(1L)).thenReturn(java.util.Optional.of(candidate));
        when(replayContestRepository.findByCandidateId(1L)).thenReturn(java.util.Optional.of(contest));

        CombatReplayContestLifecycleService.ForcePromoteResult result = service.forcePromoteCandidate(1L);

        assertFalse(result.promoted());
        assertEquals("Mentak preview window is still open.", result.reason());
        assertEquals(contest, result.contest());
    }

    @Test
    void forcePromoteCandidateCanPubliclyPromoteAfterMentakPreviewWindowExpires() {
        CombatCandidateRepository candidateRepository = mock(CombatCandidateRepository.class);
        CombatObservationRepository observationRepository = mock(CombatObservationRepository.class);
        CombatReplayContestRepository replayContestRepository = mock(CombatReplayContestRepository.class);
        CombatContestSettings settings = new CombatContestSettings();
        settings.getPromotion().setEnabled(true);
        settings.setHousesEnabled(true);
        settings.getHouseAbilities().getMentak().setPreviewLeadSeconds(900);
        CombatReplayContestLifecycleService service =
                service(settings, candidateRepository, observationRepository, replayContestRepository);
        service.setClock(fixedClock("2026-04-27T12:15:00"));
        CombatCandidateEntity candidate = previewedCandidate(LocalDateTime.parse("2026-04-27T12:00:00"));
        CombatReplayContestEntity contest = previewContest();

        when(candidateRepository.findById(1L)).thenReturn(java.util.Optional.of(candidate));
        when(replayContestRepository.findByCandidateId(1L)).thenReturn(java.util.Optional.of(contest));

        CombatReplayContestLifecycleService.ForcePromoteResult result = service.forcePromoteCandidate(1L);

        assertFalse(result.promoted());
        assertEquals("Promotion failed", result.reason());
        verify(observationRepository).findById(1L);
    }

    @Test
    void promoteBestCandidateIfDueChecksReadyMentakPreviewsAwayFromTopOfHour() {
        CombatCandidateRepository candidateRepository = mock(CombatCandidateRepository.class);
        CombatObservationRepository observationRepository = mock(CombatObservationRepository.class);
        CombatReplayContestRepository replayContestRepository = mock(CombatReplayContestRepository.class);
        CombatContestSettings settings = new CombatContestSettings();
        settings.getPromotion().setEnabled(true);
        settings.getRuntime().setDevMode(false);
        settings.setHousesEnabled(true);
        settings.getHouseAbilities().getMentak().setPreviewLeadSeconds(900);
        CombatReplayContestLifecycleService service =
                service(settings, candidateRepository, observationRepository, replayContestRepository);
        service.setClock(fixedClock("2026-04-27T12:25:00"));
        CombatCandidateEntity candidate = previewedCandidate(LocalDateTime.parse("2026-04-27T12:10:00"));

        when(candidateRepository.findResolvedPromotionCandidates(
                        CombatCandidateStatus.RESOLVED,
                        CombatCandidatePromotionStatus.PENDING,
                        LocalDateTime.parse("2026-04-27T00:25:00")))
                .thenReturn(List.of(candidate));
        when(observationRepository.findAllById(List.of(1L))).thenReturn(List.of());

        service.promoteBestCandidateIfDue();

        verify(candidateRepository)
                .findResolvedPromotionCandidates(
                        CombatCandidateStatus.RESOLVED,
                        CombatCandidatePromotionStatus.PENDING,
                        LocalDateTime.parse("2026-04-27T00:25:00"));
        verify(observationRepository).findById(1L);
    }

    private CombatReplayContestLifecycleService service(
            CombatContestSettings settings,
            CombatCandidateRepository candidateRepository,
            CombatReplayContestRepository replayContestRepository) {
        return service(settings, candidateRepository, mock(CombatObservationRepository.class), replayContestRepository);
    }

    private CombatReplayContestLifecycleService service(
            CombatContestSettings settings,
            CombatCandidateRepository candidateRepository,
            CombatObservationRepository observationRepository,
            CombatReplayContestRepository replayContestRepository) {
        CombatReplayPromotionService promotionService = new CombatReplayPromotionService(
                settings,
                candidateRepository,
                observationRepository,
                replayContestRepository,
                mock(CombatReplayLeaderboardService.class),
                mock(ti4.contest.replay.house.hacan.CombatReplayHacanTradeConvoysService.class),
                mock(CombatReplayMentakAbilityService.class),
                mock(CombatReplayExecutionService.class),
                mock(CombatReplayDiscordPostService.class));
        return new CombatReplayContestLifecycleService(promotionService, mock(CombatReplayExecutionService.class));
    }

    private Clock fixedClock(String localDateTime) {
        return Clock.fixed(
                LocalDateTime.parse(localDateTime)
                        .atZone(ZoneId.systemDefault())
                        .toInstant(),
                ZoneId.systemDefault());
    }

    private CombatCandidateEntity previewedCandidate(LocalDateTime previewedAt) {
        CombatCandidateEntity candidate = new CombatCandidateEntity();
        candidate.setId(1L);
        candidate.setObservationId(1L);
        candidate.setStatus(CombatCandidateStatus.RESOLVED);
        candidate.setPromotionStatus(CombatCandidatePromotionStatus.PENDING);
        candidate.setResolvedAt(previewedAt);
        candidate.setMentakPreviewPostedAt(previewedAt);
        candidate.setInitialRenderSnapshotJson("{\"context\":{}}");
        return candidate;
    }

    private CombatReplayContestEntity previewContest() {
        CombatReplayContestEntity contest = new CombatReplayContestEntity();
        contest.setReplayStatus(CombatContestReplayStatus.PREVIEW);
        return contest;
    }
}
