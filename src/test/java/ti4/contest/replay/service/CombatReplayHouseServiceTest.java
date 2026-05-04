package ti4.contest.replay.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import ti4.contest.replay.core.CombatContestSettings;
import ti4.contest.replay.core.CombatReplayHouse;
import ti4.contest.replay.entities.CombatReplayHouseEntity;
import ti4.contest.replay.entities.CombatReplayLeaderboardEntryEntity;
import ti4.contest.replay.repository.CombatCandidateRepository;
import ti4.contest.replay.repository.CombatReplayContestRepository;
import ti4.contest.replay.repository.CombatReplayHouseRepository;
import ti4.contest.replay.repository.CombatReplayLeaderboardEntryRepository;

class CombatReplayHouseServiceTest {

    private final CombatReplayHouseRepository houseRepository = mock(CombatReplayHouseRepository.class);
    private final CombatReplayLeaderboardEntryRepository leaderboardEntryRepository =
            mock(CombatReplayLeaderboardEntryRepository.class);
    private final CombatReplayHouseService service = new CombatReplayHouseService(
            new CombatContestSettings(),
            houseRepository,
            mock(CombatReplayContestRepository.class),
            mock(CombatCandidateRepository.class),
            leaderboardEntryRepository);

    @Test
    void seasonStartRandomlyAssignsLeaderboardEntriesIntoBalancedDelegations() {
        List<CombatReplayLeaderboardEntryEntity> entries = List.of(
                leaderboardEntry("1", "One"),
                leaderboardEntry("2", "Two"),
                leaderboardEntry("3", "Three"),
                leaderboardEntry("4", "Four"),
                leaderboardEntry("5", "Five"));

        int assigned = service.assignLeaderboardEntriesRandomly(null, entries);

        assertEquals(5, assigned);
        ArgumentCaptor<CombatReplayHouseEntity> captor = ArgumentCaptor.forClass(CombatReplayHouseEntity.class);
        verify(houseRepository, org.mockito.Mockito.times(5)).save(captor.capture());

        Map<CombatReplayHouse, Integer> countsByHouse = new EnumMap<>(CombatReplayHouse.class);
        for (CombatReplayHouse house : CombatReplayHouse.assignmentOrder()) {
            countsByHouse.put(house, 0);
        }
        for (CombatReplayHouseEntity assignment : captor.getAllValues()) {
            countsByHouse.computeIfPresent(assignment.getHouse(), (house, count) -> count + 1);
        }

        int min = countsByHouse.values().stream()
                .mapToInt(Integer::intValue)
                .min()
                .orElse(0);
        int max = countsByHouse.values().stream()
                .mapToInt(Integer::intValue)
                .max()
                .orElse(0);
        assertTrue(max - min <= 1);
    }

    @Test
    void houseAssignmentCreatesPersonalLeaderboardEntryWithStartingPoints() {
        when(houseRepository.findByDiscordUserId("1")).thenReturn(Optional.empty());
        when(leaderboardEntryRepository.findByDiscordUserId("1")).thenReturn(Optional.empty());

        service.assignHouseIfAbsent(null, null, user("1", "One"));

        ArgumentCaptor<CombatReplayLeaderboardEntryEntity> captor =
                ArgumentCaptor.forClass(CombatReplayLeaderboardEntryEntity.class);
        verify(leaderboardEntryRepository).save(captor.capture());
        CombatReplayLeaderboardEntryEntity entry = captor.getValue();
        assertEquals("1", entry.getDiscordUserId());
        assertEquals("One", entry.getDiscordUserName());
        assertEquals(
                new CombatContestSettings().getHouseAbilities().getInitialIndividualPoints(), entry.getTotalPoints());
        assertEquals(0, entry.getPredictionCount());
        assertEquals(0, entry.getCorrectPredictions());
    }

    private CombatReplayLeaderboardEntryEntity leaderboardEntry(String userId, String userName) {
        org.mockito.Mockito.when(houseRepository.findByDiscordUserId(userId)).thenReturn(Optional.empty());
        CombatReplayLeaderboardEntryEntity entry = new CombatReplayLeaderboardEntryEntity();
        entry.setDiscordUserId(userId);
        entry.setDiscordUserName(userName);
        return entry;
    }

    private net.dv8tion.jda.api.entities.User user(String userId, String userName) {
        net.dv8tion.jda.api.entities.User user = mock(net.dv8tion.jda.api.entities.User.class);
        when(user.getId()).thenReturn(userId);
        when(user.getName()).thenReturn(userName);
        return user;
    }
}
