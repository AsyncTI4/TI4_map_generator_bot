package ti4.contest.replay.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import ti4.contest.replay.core.CombatContestSettings;
import ti4.contest.replay.entities.CombatReplayLeaderboardEntryEntity;
import ti4.contest.replay.repository.CombatReplayContestRepository;
import ti4.contest.replay.repository.CombatReplayLeaderboardEntryRepository;
import ti4.contest.replay.repository.CombatReplayPredictionRepository;

class CombatReplayLeaderboardServiceTest {

    @Test
    void newLeaderboardEntriesStartWithInitialPoints() throws Exception {
        CombatReplayLeaderboardService service = newService(mock(CombatReplayLeaderboardEntryRepository.class));
        Method method = CombatReplayLeaderboardService.class.getDeclaredMethod(
                "newLeaderboardEntry", String.class, String.class);
        method.setAccessible(true);

        CombatReplayLeaderboardEntryEntity entry =
                (CombatReplayLeaderboardEntryEntity) method.invoke(service, "123", "Player");

        assertEquals(CombatReplayLeaderboardService.STARTING_POINTS, entry.getTotalPoints());
        assertEquals(0, entry.getPredictionCount());
        assertEquals(0, entry.getCorrectPredictions());
    }

    @Test
    void userPointsMessageIncludesRankPointsAndAccuracy() {
        CombatReplayLeaderboardEntryRepository repository = mock(CombatReplayLeaderboardEntryRepository.class);
        CombatReplayLeaderboardEntryEntity first = leaderboardEntry("1", "First", 140, 10, 7);
        CombatReplayLeaderboardEntryEntity user = leaderboardEntry("2", "Player", 120, 5, 3);
        when(repository.findByDiscordUserId("2")).thenReturn(Optional.of(user));
        when(repository.findAllByOrderByTotalPointsDescCorrectPredictionsDescPredictionCountDescDiscordUserNameAsc())
                .thenReturn(List.of(first, user));
        CombatReplayLeaderboardService service = newService(repository);

        String message = service.buildUserPointsMessage("2");

        assertEquals("""
                ## Your Lazax War Archives Points
                Rank: **#2**
                Points: **120**
                Correct predictions: `3/5` (60%)""", message);
    }

    @Test
    void top100MessageUsesLeaderboardFormatting() {
        CombatReplayLeaderboardEntryRepository repository = mock(CombatReplayLeaderboardEntryRepository.class);
        when(repository.findTop100ByOrderByTotalPointsDescCorrectPredictionsDescPredictionCountDescDiscordUserNameAsc())
                .thenReturn(List.of(leaderboardEntry("1", "Player", 120, 5, 3)));
        CombatReplayLeaderboardService service = newService(repository);

        String message = service.buildTop100LeaderboardMessage();

        assertEquals(
                "## Lazax War Archives Leaderboard\n" + "`1.` Player - **120** points (`3/5` correct, 60%)", message);
    }

    private static CombatReplayLeaderboardService newService(CombatReplayLeaderboardEntryRepository repository) {
        return new CombatReplayLeaderboardService(
                new CombatContestSettings(),
                mock(CombatReplayContestRepository.class),
                mock(CombatReplayPredictionRepository.class),
                repository,
                mock(CombatReplaySideBetService.class));
    }

    private static CombatReplayLeaderboardEntryEntity leaderboardEntry(
            String userId, String userName, int totalPoints, int predictionCount, int correctPredictions) {
        CombatReplayLeaderboardEntryEntity entry = new CombatReplayLeaderboardEntryEntity();
        entry.setDiscordUserId(userId);
        entry.setDiscordUserName(userName);
        entry.setTotalPoints(totalPoints);
        entry.setPredictionCount(predictionCount);
        entry.setCorrectPredictions(correctPredictions);
        entry.setUpdatedAt(LocalDateTime.now());
        return entry;
    }
}
