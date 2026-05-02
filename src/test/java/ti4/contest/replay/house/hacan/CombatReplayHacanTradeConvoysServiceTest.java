package ti4.contest.replay.house.hacan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import ti4.contest.replay.core.CombatContestSettings;
import ti4.contest.replay.core.CombatReplayHouse;
import ti4.contest.replay.entities.CombatCandidateEntity;
import ti4.contest.replay.entities.CombatReplayContestEntity;
import ti4.contest.replay.entities.CombatReplayHacanTradeConvoysEntity;
import ti4.contest.replay.entities.CombatReplayHouseScoreEntity;
import ti4.contest.replay.repository.CombatReplayContestRepository;
import ti4.contest.replay.repository.CombatReplayHacanTradeConvoysRepository;
import ti4.contest.replay.repository.CombatReplayHacanTradeConvoysVoteRepository;
import ti4.contest.replay.repository.CombatReplayHouseAbilityUseRepository;
import ti4.contest.replay.repository.CombatReplayHouseScoreRepository;
import ti4.contest.replay.service.CombatReplayHouseFavorService;
import ti4.contest.replay.service.CombatReplayHouseService;

class CombatReplayHacanTradeConvoysServiceTest {

    private final CombatReplayHacanTradeConvoysRepository tradeConvoysRepository =
            mock(CombatReplayHacanTradeConvoysRepository.class);
    private final CombatReplayContestRepository contestRepository = mock(CombatReplayContestRepository.class);
    private final CombatReplayHouseScoreRepository houseScoreRepository = mock(CombatReplayHouseScoreRepository.class);
    private final CombatReplayHouseFavorService houseFavorService = mock(CombatReplayHouseFavorService.class);
    private final CombatReplayHacanTradeConvoysService service = new CombatReplayHacanTradeConvoysService(
            new CombatContestSettings(),
            mock(CombatReplayHouseService.class),
            houseFavorService,
            contestRepository,
            mock(CombatReplayHouseAbilityUseRepository.class),
            tradeConvoysRepository,
            mock(CombatReplayHacanTradeConvoysVoteRepository.class),
            houseScoreRepository);

    @Test
    void tradeConvoysForNextCombatUsesPriorUnscoredConvoy() {
        when(tradeConvoysRepository.findAll()).thenReturn(List.of(convoy(1L, CombatReplayHouse.NAALU, 20, 10)));
        when(houseScoreRepository.findAll()).thenReturn(List.of(score(2L)));

        CombatReplayHacanTradeConvoysService.TradeConvoys active = service.tradeConvoysForNextCombat(2L);
        CombatReplayHacanTradeConvoysService.TradeConvoys expired = service.tradeConvoysForNextCombat(3L);

        assertEquals(1L, active.sourceContestId());
        assertEquals(CombatReplayHouse.NAALU, active.targetHouse());
        assertEquals(10, active.bonusPercent());
        assertFalse(expired.active());
    }

    @Test
    void tradeConvoysBonusPointsRoundsToNearestPointAndNeverPenalizesHacan() {
        assertEquals(2, CombatReplayHacanTradeConvoysService.tradeConvoysBonusPoints(13, 15));
        assertEquals(0, CombatReplayHacanTradeConvoysService.tradeConvoysBonusPoints(-4, 15));
    }

    @Test
    void tradeConvoysVotingOnlyOpensAfterCombatAndBeforeNextContest() {
        CombatCandidateEntity candidate = new CombatCandidateEntity();
        candidate.setId(7L);
        CombatReplayContestEntity contest = new CombatReplayContestEntity();
        contest.setId(3L);
        contest.setCandidateId(candidate.getId());
        when(tradeConvoysRepository.findByContestId(contest.getId())).thenReturn(Optional.empty());
        when(contestRepository.existsByIdGreaterThan(contest.getId())).thenReturn(false);

        assertFalse(service.shouldOfferVoting(contest, candidate));

        contest.setReplayCompletedAt(LocalDateTime.now());

        assertTrue(service.shouldOfferVoting(contest, candidate));

        when(contestRepository.existsByIdGreaterThan(contest.getId())).thenReturn(true);

        assertFalse(service.shouldOfferVoting(contest, candidate));
    }

    @Test
    void tradeConvoysButtonsStayVisibleButDisableUnaffordableFavorCosts() {
        CombatReplayContestEntity contest = new CombatReplayContestEntity();
        contest.setId(3L);
        when(houseFavorService.canAfford(CombatReplayHouse.HACAN, 10)).thenReturn(true);
        when(houseFavorService.canAfford(CombatReplayHouse.HACAN, 20)).thenReturn(false);
        when(houseFavorService.canAfford(CombatReplayHouse.HACAN, 30)).thenReturn(false);

        List<net.dv8tion.jda.api.components.buttons.Button> buttons =
                service.tradeConvoysButtonsForHouse(contest, CombatReplayHouse.NAALU);

        assertEquals(3, buttons.size());
        assertFalse(buttons.get(0).isDisabled());
        assertTrue(buttons.get(1).isDisabled());
        assertTrue(buttons.get(2).isDisabled());
    }

    private CombatReplayHacanTradeConvoysEntity convoy(
            Long contestId, CombatReplayHouse targetHouse, int favorCost, int bonusPercent) {
        CombatReplayHacanTradeConvoysEntity convoy = new CombatReplayHacanTradeConvoysEntity();
        convoy.setContestId(contestId);
        convoy.setTargetHouse(targetHouse);
        convoy.setFavorCost(favorCost);
        convoy.setPredictionBonus(bonusPercent);
        convoy.setVoteCount(1);
        return convoy;
    }

    private CombatReplayHouseScoreEntity score(Long contestId) {
        CombatReplayHouseScoreEntity score = new CombatReplayHouseScoreEntity();
        score.setContestId(contestId);
        return score;
    }
}
