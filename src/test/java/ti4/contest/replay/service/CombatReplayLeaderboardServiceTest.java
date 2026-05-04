package ti4.contest.replay.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.Test;
import ti4.contest.replay.core.CombatContestSettings;
import ti4.contest.replay.core.CombatReplayHouse;
import ti4.contest.replay.entities.CombatReplayLeaderboardEntryEntity;
import ti4.contest.replay.house.hacan.CombatReplayHacanTradeConvoysService;
import ti4.contest.replay.repository.CombatReplayContestRepository;
import ti4.contest.replay.repository.CombatReplayLeaderboardEntryRepository;
import ti4.contest.replay.repository.CombatReplayPredictionRepository;
import ti4.contest.replay.service.CombatReplayHouseLedgerService.HouseFavorSummary;
import ti4.contest.replay.service.CombatReplayHouseLedgerService.HousePredictionSummary;

class CombatReplayLeaderboardServiceTest {

    @Test
    void newLeaderboardEntriesStartWithInitialPoints() throws Exception {
        CombatReplayLeaderboardService service = new CombatReplayLeaderboardService(
                new CombatContestSettings(),
                mock(CombatReplayContestRepository.class),
                mock(CombatReplayPredictionRepository.class),
                mock(CombatReplayLeaderboardEntryRepository.class),
                mock(CombatReplaySideBetService.class),
                mock(CombatReplayHouseService.class),
                mock(CombatReplayHouseLedgerService.class),
                mock(CombatReplayHouseFavorService.class),
                mock(CombatReplayHacanTradeConvoysService.class));
        Method method = CombatReplayLeaderboardService.class.getDeclaredMethod(
                "newLeaderboardEntry", String.class, String.class);
        method.setAccessible(true);

        CombatReplayLeaderboardEntryEntity entry =
                (CombatReplayLeaderboardEntryEntity) method.invoke(service, "123", "Player");

        assertEquals(
                new CombatContestSettings().getHouseAbilities().getInitialIndividualPoints(), entry.getTotalPoints());
        assertEquals(0, entry.getPredictionCount());
        assertEquals(0, entry.getCorrectPredictions());
    }

    @Test
    void hacanFavorAwardMessageBreaksOutMarketCompactFavor() throws Exception {
        CombatReplayLeaderboardService service = new CombatReplayLeaderboardService(
                new CombatContestSettings(),
                mock(CombatReplayContestRepository.class),
                mock(CombatReplayPredictionRepository.class),
                mock(CombatReplayLeaderboardEntryRepository.class),
                mock(CombatReplaySideBetService.class),
                mock(CombatReplayHouseService.class),
                mock(CombatReplayHouseLedgerService.class),
                mock(CombatReplayHouseFavorService.class),
                mock(CombatReplayHacanTradeConvoysService.class));
        Method method = CombatReplayLeaderboardService.class.getDeclaredMethod(
                "favorAwardMessage", HousePredictionSummary.class);
        method.setAccessible(true);

        HousePredictionSummary summary = new HousePredictionSummary(
                CombatReplayHouse.HACAN,
                0,
                0,
                0,
                13,
                0,
                0,
                List.of(),
                List.of(
                        new HouseFavorSummary("the Custodians sealing this combat's ledger", 10),
                        new HouseFavorSummary("Market Compact hits", 3)));

        String message = (String) method.invoke(service, summary);

        assertTrue(message.contains("receives `+13` Favor"));
        assertTrue(message.contains("- `+10` from the Custodians sealing this combat's ledger."));
        assertTrue(message.contains("- `+3` from Market Compact hits."));
        assertTrue(message.contains("- **Total Favor:** `0`"));
    }
}
