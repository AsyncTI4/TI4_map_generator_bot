package ti4.contest.replay.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import ti4.contest.replay.core.CombatContestSettings;
import ti4.contest.replay.entities.CombatReplayLeaderboardEntryEntity;
import ti4.contest.replay.repository.CombatReplayContestRepository;
import ti4.contest.replay.repository.CombatReplayLeaderboardEntryRepository;
import ti4.contest.replay.repository.CombatReplayPredictionRepository;

class CombatReplayLeaderboardServiceTest {

    @Test
    void newLeaderboardEntriesStartWithInitialPoints() throws Exception {
        CombatReplayLeaderboardService service = new CombatReplayLeaderboardService(
                new CombatContestSettings(),
                mock(CombatReplayContestRepository.class),
                mock(CombatReplayPredictionRepository.class),
                mock(CombatReplayLeaderboardEntryRepository.class),
                mock(CombatReplaySideBetService.class));
        Method method = CombatReplayLeaderboardService.class.getDeclaredMethod(
                "newLeaderboardEntry", String.class, String.class);
        method.setAccessible(true);

        CombatReplayLeaderboardEntryEntity entry =
                (CombatReplayLeaderboardEntryEntity) method.invoke(service, "123", "Player");

        assertEquals(CombatReplayLeaderboardService.STARTING_POINTS, entry.getTotalPoints());
        assertEquals(0, entry.getPredictionCount());
        assertEquals(0, entry.getCorrectPredictions());
    }
}
