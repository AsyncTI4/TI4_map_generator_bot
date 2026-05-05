package ti4.contest.replay.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
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
import ti4.game.Game;
import ti4.game.persistence.GameManager;
import ti4.game.persistence.ManagedGame;

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
    void promoteBestCandidateIfDueChecksReadyMentakPreviewsWhenCronRunsLateForHourlySlot() {
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
        CombatCandidateEntity candidate = previewedCandidate(LocalDateTime.parse("2026-04-27T11:45:00"));

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

    @Test
    void promoteBestCandidateIfDueUsesActualTimeWhenPreviewBecomesReadyMidHour() {
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

    @Test
    void promoteBestCandidateIfDueFallsBackWhenMentakPreviewPostingFails() {
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
        service.setClock(fixedClock("2026-04-27T12:50:00"));
        CombatCandidateEntity first = promotionCandidate(1L, LocalDateTime.parse("2026-04-27T12:20:00"), 10.0);
        CombatCandidateEntity second = promotionCandidate(2L, LocalDateTime.parse("2026-04-27T12:25:00"), 5.0);

        when(candidateRepository.findResolvedPromotionCandidates(
                        CombatCandidateStatus.RESOLVED,
                        CombatCandidatePromotionStatus.PENDING,
                        LocalDateTime.parse("2026-04-27T00:50:00")))
                .thenReturn(List.of(first, second));
        when(observationRepository.findAllById(List.of(1L, 2L))).thenReturn(List.of());
        when(observationRepository.findById(1L)).thenReturn(java.util.Optional.empty());
        when(observationRepository.findById(2L)).thenReturn(java.util.Optional.empty());

        service.promoteBestCandidateIfDue();

        verify(observationRepository).findById(1L);
        verify(observationRepository).findById(2L);
    }

    @Test
    void promoteBestCandidateIfDueFallsBackWhenReadyMentakPreviewPromotionFails() {
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
        CombatCandidateEntity first = previewedCandidate(1L, LocalDateTime.parse("2026-04-27T11:45:00"), 10.0);
        CombatCandidateEntity second = previewedCandidate(2L, LocalDateTime.parse("2026-04-27T11:45:00"), 5.0);

        when(candidateRepository.findResolvedPromotionCandidates(
                        CombatCandidateStatus.RESOLVED,
                        CombatCandidatePromotionStatus.PENDING,
                        LocalDateTime.parse("2026-04-27T00:25:00")))
                .thenReturn(List.of(first, second));
        when(observationRepository.findAllById(List.of(1L, 2L))).thenReturn(List.of());
        when(observationRepository.findById(1L)).thenReturn(java.util.Optional.empty());
        when(observationRepository.findById(2L)).thenReturn(java.util.Optional.empty());

        service.promoteBestCandidateIfDue();

        verify(observationRepository).findById(1L);
        verify(observationRepository).findById(2L);
    }

    @Test
    void promoteBestCandidateIfDueExpiresDiscordantStarsCandidateAndContinues() {
        CombatCandidateRepository candidateRepository = mock(CombatCandidateRepository.class);
        CombatObservationRepository observationRepository = mock(CombatObservationRepository.class);
        CombatReplayContestRepository replayContestRepository = mock(CombatReplayContestRepository.class);
        CombatContestSettings settings = new CombatContestSettings();
        settings.getPromotion().setEnabled(true);
        settings.setHousesEnabled(false);
        settings.getRuntime().setDevMode(false);
        CombatReplayContestLifecycleService service =
                service(settings, candidateRepository, observationRepository, replayContestRepository);
        service.setClock(fixedClock("2026-04-27T12:00:00"));
        CombatCandidateEntity discordantStarsCandidate =
                promotionCandidate(1L, LocalDateTime.parse("2026-04-27T11:30:00"), 10.0);
        discordantStarsCandidate.setGameName("pbd-ds");
        CombatCandidateEntity nextBestCandidate =
                promotionCandidate(2L, LocalDateTime.parse("2026-04-27T11:35:00"), 5.0);
        nextBestCandidate.setGameName("pbd-pok");
        Game discordantStarsGame = game("pbd-ds", true);
        Game normalGame = game("pbd-pok", false);
        ManagedGame discordantStarsManagedGame = mock(ManagedGame.class);
        ManagedGame normalManagedGame = mock(ManagedGame.class);

        when(replayContestRepository.countByPostedAtGreaterThanEqual(any())).thenReturn(0L);
        when(candidateRepository.findResolvedPromotionCandidates(
                        eq(CombatCandidateStatus.RESOLVED), eq(CombatCandidatePromotionStatus.PENDING), any()))
                .thenReturn(List.of(discordantStarsCandidate, nextBestCandidate));
        when(observationRepository.findAllById(List.of(2L))).thenReturn(List.of());
        when(observationRepository.findById(2L)).thenReturn(java.util.Optional.empty());
        when(discordantStarsManagedGame.getGame()).thenReturn(discordantStarsGame);
        when(normalManagedGame.getGame()).thenReturn(normalGame);

        try (MockedStatic<GameManager> gameManager = org.mockito.Mockito.mockStatic(GameManager.class)) {
            gameManager.when(() -> GameManager.getManagedGame("pbd-ds")).thenReturn(discordantStarsManagedGame);
            gameManager.when(() -> GameManager.getManagedGame("pbd-pok")).thenReturn(normalManagedGame);

            service.promoteBestCandidateIfDue();
        }

        Assertions.assertEquals(CombatCandidatePromotionStatus.EXPIRED, discordantStarsCandidate.getPromotionStatus());
        verify(candidateRepository).save(discordantStarsCandidate);
        verify(observationRepository).findById(2L);
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
                mock(CombatReplayHouseService.class),
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
        return previewedCandidate(1L, previewedAt, null);
    }

    private CombatCandidateEntity previewedCandidate(Long id, LocalDateTime previewedAt, Double promotionScore) {
        CombatCandidateEntity candidate = new CombatCandidateEntity();
        candidate.setId(id);
        candidate.setObservationId(id);
        candidate.setStatus(CombatCandidateStatus.RESOLVED);
        candidate.setPromotionStatus(CombatCandidatePromotionStatus.PENDING);
        candidate.setResolvedAt(previewedAt);
        candidate.setMentakPreviewPostedAt(previewedAt);
        candidate.setPromotionScore(promotionScore);
        candidate.setInitialRenderSnapshotJson("{\"context\":{}}");
        return candidate;
    }

    private CombatCandidateEntity promotionCandidate(Long id, LocalDateTime resolvedAt, Double promotionScore) {
        CombatCandidateEntity candidate = previewedCandidate(id, resolvedAt, promotionScore);
        candidate.setMentakPreviewPostedAt(null);
        return candidate;
    }

    private CombatReplayContestEntity previewContest() {
        CombatReplayContestEntity contest = new CombatReplayContestEntity();
        contest.setReplayStatus(CombatContestReplayStatus.PREVIEW);
        return contest;
    }

    private Game game(String name, boolean discordantStarsMode) {
        Game game = new Game();
        game.setName(name);
        game.setDiscordantStarsMode(discordantStarsMode);
        return game;
    }
}
