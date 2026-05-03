package ti4.contest.replay.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.junit.jupiter.api.Test;
import ti4.contest.replay.core.CombatContestSettings;
import ti4.contest.replay.core.CombatReplayHouse;
import ti4.contest.replay.core.CombatSideBetType;
import ti4.contest.replay.entities.CombatCandidateEntity;
import ti4.contest.replay.entities.CombatContestSideBetEntity;
import ti4.contest.replay.entities.CombatReplayContestEntity;
import ti4.contest.replay.entities.CombatReplayLeaderboardEntryEntity;
import ti4.contest.replay.repository.CombatCandidateRepository;
import ti4.contest.replay.repository.CombatContestSideBetRepository;
import ti4.contest.replay.repository.CombatReplayContestRepository;
import ti4.contest.replay.repository.CombatReplayLeaderboardEntryRepository;

class CombatReplaySideBetServiceTest {

    private final CombatContestSettings settings = new CombatContestSettings();
    private final CombatReplayContestRepository replayContestRepository = mock(CombatReplayContestRepository.class);
    private final CombatCandidateRepository candidateRepository = mock(CombatCandidateRepository.class);
    private final CombatContestSideBetRepository sideBetRepository = mock(CombatContestSideBetRepository.class);
    private final CombatReplayLeaderboardEntryRepository leaderboardEntryRepository =
            mock(CombatReplayLeaderboardEntryRepository.class);
    private final CombatReplaySideBetPayoutService payoutService = mock(CombatReplaySideBetPayoutService.class);
    private final CombatReplaySideBetUiService sideBetUiService = mock(CombatReplaySideBetUiService.class);
    private final CombatReplayHouseService houseService = mock(CombatReplayHouseService.class);
    private final CombatReplaySideBetService service = new CombatReplaySideBetService(
            settings,
            replayContestRepository,
            candidateRepository,
            sideBetRepository,
            leaderboardEntryRepository,
            payoutService,
            sideBetUiService,
            houseService);

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
    void hacanUserWithZeroPointsCanStillPlaceSideBetAndStaysAtZero() {
        ButtonInteractionEvent event = mock(ButtonInteractionEvent.class);
        User user = mock(User.class);
        MessageChannel channel = mock(MessageChannel.class);
        CombatReplayContestEntity contest = contest();
        CombatCandidateEntity candidate = candidate(0, 0, false, false);
        candidate.setId(7L);
        candidate.setSideBetCompatible(true);
        CombatReplayLeaderboardEntryEntity entry = leaderboardEntry("user-id", "Hacan Player", 0);

        when(event.getUser()).thenReturn(user);
        when(event.getMessageChannel()).thenReturn(channel);
        when(user.getId()).thenReturn("user-id");
        when(user.getEffectiveName()).thenReturn("Hacan Player");
        when(replayContestRepository.findById(1L)).thenReturn(Optional.of(contest));
        when(candidateRepository.findById(7L)).thenReturn(Optional.of(candidate));
        when(leaderboardEntryRepository.findByDiscordUserId("user-id")).thenReturn(Optional.of(entry));
        when(houseService.houseForUser("user-id")).thenReturn(CombatReplayHouse.HACAN);
        when(sideBetRepository.countByContestIdAndDiscordUserId(1L, "user-id")).thenReturn(0);
        when(payoutService.offeredPayout(contest, candidate, CombatSideBetType.ROUND_ONE_WHIFF, "sol"))
                .thenReturn(10);
        when(sideBetUiService.renderUserSummary(contest, candidate, "user-id", 0))
                .thenReturn("summary");

        CombatReplaySideBetService.PlacementResult result =
                service.placeSideBet(event, 1L, CombatSideBetType.ROUND_ONE_WHIFF, "sol");

        assertTrue(result.accepted());
        assertEquals(0, result.totalPoints());
        assertEquals(0, entry.getTotalPoints());
        verify(sideBetRepository).save(any(CombatContestSideBetEntity.class));
    }

    @Test
    void hacanUserWithoutLeaderboardEntryCanStillPlaceSideBetAtZero() {
        ButtonInteractionEvent event = mock(ButtonInteractionEvent.class);
        User user = mock(User.class);
        MessageChannel channel = mock(MessageChannel.class);
        CombatReplayContestEntity contest = contest();
        CombatCandidateEntity candidate = candidate(0, 0, false, false);
        candidate.setId(7L);
        candidate.setSideBetCompatible(true);

        when(event.getUser()).thenReturn(user);
        when(event.getMessageChannel()).thenReturn(channel);
        when(user.getId()).thenReturn("user-id");
        when(user.getEffectiveName()).thenReturn("Hacan Player");
        when(replayContestRepository.findById(1L)).thenReturn(Optional.of(contest));
        when(candidateRepository.findById(7L)).thenReturn(Optional.of(candidate));
        when(leaderboardEntryRepository.findByDiscordUserId("user-id")).thenReturn(Optional.empty());
        when(houseService.houseForUser("user-id")).thenReturn(CombatReplayHouse.HACAN);
        when(sideBetRepository.countByContestIdAndDiscordUserId(1L, "user-id")).thenReturn(0);
        when(payoutService.offeredPayout(contest, candidate, CombatSideBetType.ROUND_ONE_WHIFF, "sol"))
                .thenReturn(10);
        when(sideBetUiService.renderUserSummary(contest, candidate, "user-id", 0))
                .thenReturn("summary");

        CombatReplaySideBetService.PlacementResult result =
                service.placeSideBet(event, 1L, CombatSideBetType.ROUND_ONE_WHIFF, "sol");

        assertTrue(result.accepted());
        assertEquals(0, result.totalPoints());
        verify(leaderboardEntryRepository).save(any(CombatReplayLeaderboardEntryEntity.class));
        verify(sideBetRepository).save(any(CombatContestSideBetEntity.class));
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

    private CombatReplayContestEntity contest() {
        CombatReplayContestEntity contest = new CombatReplayContestEntity();
        contest.setId(1L);
        contest.setCandidateId(7L);
        contest.setReplayStartAt(LocalDateTime.now().plusMinutes(1));
        contest.setSideBetMarketPostedAt(LocalDateTime.now());
        return contest;
    }

    private CombatReplayLeaderboardEntryEntity leaderboardEntry(String userId, String userName, int points) {
        CombatReplayLeaderboardEntryEntity entry = new CombatReplayLeaderboardEntryEntity();
        entry.setDiscordUserId(userId);
        entry.setDiscordUserName(userName);
        entry.setTotalPoints(points);
        entry.setPredictionCount(0);
        entry.setCorrectPredictions(0);
        entry.setUpdatedAt(LocalDateTime.now());
        return entry;
    }
}
