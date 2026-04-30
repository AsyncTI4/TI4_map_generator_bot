package ti4.contest.replay.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import ti4.contest.replay.core.CombatCandidateEventType;
import ti4.contest.replay.core.CombatContestSettings;
import ti4.contest.replay.core.CombatSideBetType;
import ti4.contest.replay.entities.CombatCandidateEntity;
import ti4.contest.replay.entities.CombatContestSideBetEntity;
import ti4.contest.replay.entities.CombatReplayContestEntity;
import ti4.contest.replay.entities.CombatReplayLeaderboardEntryEntity;
import ti4.contest.replay.repository.CombatCandidateEventRepository;
import ti4.contest.replay.repository.CombatCandidateRepository;
import ti4.contest.replay.repository.CombatContestSideBetRepository;
import ti4.contest.replay.repository.CombatReplayContestRepository;
import ti4.contest.replay.repository.CombatReplayLeaderboardEntryRepository;

class CombatReplaySideBetServiceTest {

    private final CombatReplaySideBetService service = new CombatReplaySideBetService(
            new CombatContestSettings(),
            mock(CombatReplayContestRepository.class),
            mock(CombatCandidateRepository.class),
            mock(CombatCandidateEventRepository.class),
            mock(CombatContestSideBetRepository.class),
            mock(CombatReplayLeaderboardEntryRepository.class),
            mock(CombatReplaySideBetPayoutService.class),
            mock(CombatReplaySideBetUiService.class));

    @Test
    void afbSkippedAvailableForSideWithDestroyerUnlessSingleDestroyerIsFacingAssaultCannon() {
        CombatCandidateEntity candidate = candidate(1, 2, false, true);

        assertFalse(service.isAfbSkippedAvailable(candidate, "sol"));
        assertTrue(service.isAfbSkippedAvailable(candidate, "yin"));
    }

    @Test
    void afbSkippedUnavailableWithoutDestroyers() {
        CombatCandidateEntity candidate = candidate(0, 1, false, false);

        assertFalse(service.isAfbSkippedAvailable(candidate, "sol"));
        assertTrue(service.isAfbSkippedAvailable(candidate, "yin"));
    }

    @Test
    void afbSkippedBetWinsEvenIfAfbIsRolledLater() {
        CombatContestSideBetRepository sideBetRepository = mock(CombatContestSideBetRepository.class);
        CombatReplayLeaderboardEntryRepository leaderboardEntryRepository =
                mock(CombatReplayLeaderboardEntryRepository.class);
        CombatReplaySideBetPayoutService payoutService = mock(CombatReplaySideBetPayoutService.class);
        CombatReplaySideBetService resolvingService = new CombatReplaySideBetService(
                new CombatContestSettings(),
                mock(CombatReplayContestRepository.class),
                mock(CombatCandidateRepository.class),
                mock(CombatCandidateEventRepository.class),
                sideBetRepository,
                leaderboardEntryRepository,
                payoutService,
                mock(CombatReplaySideBetUiService.class));
        CombatCandidateEntity candidate = candidate(0, 2, false, false);
        candidate.setDefenderRolledAfb(true);
        candidate.setDefenderSkippedAfb(true);
        CombatReplayContestEntity contest = new CombatReplayContestEntity();
        contest.setId(126L);
        CombatContestSideBetEntity sideBet = new CombatContestSideBetEntity();
        sideBet.setContestId(126L);
        sideBet.setDiscordUserId("user");
        sideBet.setDiscordUserName("User");
        sideBet.setBetType(CombatSideBetType.AFB_SKIPPED);
        sideBet.setTargetFaction("yin");
        sideBet.setPlacedAt(LocalDateTime.now());
        CombatReplayLeaderboardEntryEntity entry = new CombatReplayLeaderboardEntryEntity();
        entry.setDiscordUserId("user");
        entry.setDiscordUserName("User");
        entry.setTotalPoints(10);

        when(sideBetRepository.findByContestId(126L)).thenReturn(List.of(sideBet));
        when(leaderboardEntryRepository.findByDiscordUserIdIn(any())).thenReturn(List.of(entry));
        when(payoutService.resolvedProfitPoints(sideBet)).thenReturn(6);

        CombatReplaySideBetService.SideBetResolution resolution = resolvingService.resolveSideBets(candidate, contest);

        assertEquals(16, entry.getTotalPoints());
        assertEquals(1, resolution.resolvedSideBets().size());
        assertEquals("Skips AFB", resolution.resolvedSideBets().getFirst().label());
        verify(sideBetRepository).saveAll(List.of(sideBet));
        verify(leaderboardEntryRepository).saveAll(any());
    }

    @Test
    void afbSkippedBetWinsFromRecordedSkipEventForLegacyCandidates() {
        CombatCandidateRepository candidateRepository = mock(CombatCandidateRepository.class);
        CombatCandidateEventRepository candidateEventRepository = mock(CombatCandidateEventRepository.class);
        CombatContestSideBetRepository sideBetRepository = mock(CombatContestSideBetRepository.class);
        CombatReplayLeaderboardEntryRepository leaderboardEntryRepository =
                mock(CombatReplayLeaderboardEntryRepository.class);
        CombatReplaySideBetPayoutService payoutService = mock(CombatReplaySideBetPayoutService.class);
        CombatReplaySideBetService resolvingService = new CombatReplaySideBetService(
                new CombatContestSettings(),
                mock(CombatReplayContestRepository.class),
                candidateRepository,
                candidateEventRepository,
                sideBetRepository,
                leaderboardEntryRepository,
                payoutService,
                mock(CombatReplaySideBetUiService.class));
        CombatCandidateEntity candidate = candidate(0, 2, false, false);
        candidate.setId(487L);
        candidate.setDefenderRolledAfb(true);
        CombatReplayContestEntity contest = new CombatReplayContestEntity();
        contest.setId(126L);
        CombatContestSideBetEntity sideBet = new CombatContestSideBetEntity();
        sideBet.setContestId(126L);
        sideBet.setDiscordUserId("user");
        sideBet.setDiscordUserName("User");
        sideBet.setBetType(CombatSideBetType.AFB_SKIPPED);
        sideBet.setTargetFaction("yin");
        CombatReplayLeaderboardEntryEntity entry = new CombatReplayLeaderboardEntryEntity();
        entry.setDiscordUserId("user");
        entry.setDiscordUserName("User");
        entry.setTotalPoints(10);

        when(sideBetRepository.findByContestId(126L)).thenReturn(List.of(sideBet));
        when(leaderboardEntryRepository.findByDiscordUserIdIn(any())).thenReturn(List.of(entry));
        when(payoutService.resolvedProfitPoints(sideBet)).thenReturn(6);
        when(candidateEventRepository.existsByCandidateIdAndEventTypeAndActorFactionAndSummaryTextContainingIgnoreCase(
                        487L, CombatCandidateEventType.INFO, "yin", "Skipped AFB"))
                .thenReturn(true);

        CombatReplaySideBetService.SideBetResolution resolution = resolvingService.resolveSideBets(candidate, contest);

        assertEquals(16, entry.getTotalPoints());
        assertTrue(candidate.getDefenderSkippedAfb());
        assertEquals("Skips AFB", resolution.resolvedSideBets().getFirst().label());
        verify(candidateRepository).save(candidate);
    }

    private CombatCandidateEntity candidate(
            int attackerDestroyers,
            int defenderDestroyers,
            boolean attackerHasAssaultCannon,
            boolean defenderHasAssaultCannon) {
        CombatCandidateEntity candidate = new CombatCandidateEntity();
        candidate.setAttackerFaction("sol");
        candidate.setDefenderFaction("yin");
        candidate.setAttackerDestroyerCount(attackerDestroyers);
        candidate.setDefenderDestroyerCount(defenderDestroyers);
        candidate.setAttackerHasAssaultCannon(attackerHasAssaultCannon);
        candidate.setDefenderHasAssaultCannon(defenderHasAssaultCannon);
        return candidate;
    }
}
