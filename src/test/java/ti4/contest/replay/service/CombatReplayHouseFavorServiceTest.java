package ti4.contest.replay.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import ti4.contest.replay.core.CombatReplayHouse;
import ti4.contest.replay.entities.CombatReplayHouseAbilityUseEntity;
import ti4.contest.replay.entities.CombatReplayHouseScoreEntity;
import ti4.contest.replay.repository.CombatReplayHouseAbilityUseRepository;
import ti4.contest.replay.repository.CombatReplayHouseScoreRepository;

class CombatReplayHouseFavorServiceTest {

    private final CombatReplayHouseScoreRepository houseScoreRepository = mock(CombatReplayHouseScoreRepository.class);
    private final CombatReplayHouseAbilityUseRepository houseAbilityUseRepository =
            mock(CombatReplayHouseAbilityUseRepository.class);
    private final CombatReplayHouseFavorService service =
            new CombatReplayHouseFavorService(houseScoreRepository, houseAbilityUseRepository);

    @Test
    void adjustFavorForAdminIncreasesAvailableBalanceWithoutChangingSpend() {
        CombatReplayHouseScoreEntity combatScore = score(12L, CombatReplayHouse.HACAN, 20);
        CombatReplayHouseScoreEntity adminAdjustment = score(0L, CombatReplayHouse.HACAN, 5);
        CombatReplayHouseAbilityUseEntity spend = use(CombatReplayHouse.HACAN, 7);
        when(houseScoreRepository.findByHouse(CombatReplayHouse.HACAN))
                .thenReturn(List.of(combatScore, adminAdjustment));
        when(houseScoreRepository.findByContestId(0L)).thenReturn(List.of(adminAdjustment));
        when(houseAbilityUseRepository.findByHouse(CombatReplayHouse.HACAN)).thenReturn(List.of(spend));

        CombatReplayHouseFavorService.FavorLedger ledger = service.adjustFavorForAdmin(CombatReplayHouse.HACAN, 12);

        assertEquals(30, ledger.balance());
        assertEquals(17, adminAdjustment.getFavorPoints());
        verify(houseScoreRepository).saveAndFlush(adminAdjustment);
    }

    @Test
    void adjustFavorForAdminCanRemoveFavorWithoutChangingSpend() {
        CombatReplayHouseScoreEntity combatScore = score(12L, CombatReplayHouse.HACAN, 20);
        CombatReplayHouseScoreEntity adminAdjustment = score(0L, CombatReplayHouse.HACAN, 5);
        CombatReplayHouseAbilityUseEntity spend = use(CombatReplayHouse.HACAN, 7);
        when(houseScoreRepository.findByHouse(CombatReplayHouse.HACAN))
                .thenReturn(List.of(combatScore, adminAdjustment));
        when(houseScoreRepository.findByContestId(0L)).thenReturn(List.of(adminAdjustment));
        when(houseAbilityUseRepository.findByHouse(CombatReplayHouse.HACAN)).thenReturn(List.of(spend));

        CombatReplayHouseFavorService.FavorLedger ledger = service.adjustFavorForAdmin(CombatReplayHouse.HACAN, -12);

        assertEquals(6, ledger.balance());
        assertEquals(-7, adminAdjustment.getFavorPoints());
        verify(houseScoreRepository).saveAndFlush(adminAdjustment);
    }

    private CombatReplayHouseScoreEntity score(Long contestId, CombatReplayHouse house, int favorPoints) {
        CombatReplayHouseScoreEntity score = new CombatReplayHouseScoreEntity();
        score.setContestId(contestId);
        score.setHouse(house);
        score.setPredictionPoints(0);
        score.setSideBetPoints(0);
        score.setAbilityPoints(0);
        score.setFavorPoints(favorPoints);
        score.setTotalPoints(0);
        score.setPredictionCount(0);
        score.setCorrectPredictions(0);
        score.setAbilityBreakdownJson("[]");
        return score;
    }

    private CombatReplayHouseAbilityUseEntity use(CombatReplayHouse house, int favorCost) {
        CombatReplayHouseAbilityUseEntity use = new CombatReplayHouseAbilityUseEntity();
        use.setHouse(house);
        use.setFavorCost(favorCost);
        return use;
    }
}
