package ti4.contest.replay.house.hacan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import ti4.contest.replay.core.CombatContestSettings;
import ti4.contest.replay.core.CombatReplayHouse;
import ti4.contest.replay.entities.CombatCandidateEntity;
import ti4.contest.replay.entities.CombatReplayContestEntity;
import ti4.contest.replay.entities.CombatReplayHacanTradeConvoysEntity;
import ti4.contest.replay.entities.CombatReplayHacanTradeConvoysVoteEntity;
import ti4.contest.replay.entities.CombatReplayHouseScoreEntity;
import ti4.contest.replay.repository.CombatCandidateRepository;
import ti4.contest.replay.repository.CombatReplayContestRepository;
import ti4.contest.replay.repository.CombatReplayHacanTradeConvoysRepository;
import ti4.contest.replay.repository.CombatReplayHacanTradeConvoysVoteRepository;
import ti4.contest.replay.repository.CombatReplayHouseAbilityUseRepository;
import ti4.contest.replay.repository.CombatReplayHouseAbilityVoteRepository;
import ti4.contest.replay.repository.CombatReplayHouseScoreRepository;
import ti4.contest.replay.service.CombatReplayHouseAbilityVoteService;
import ti4.contest.replay.service.CombatReplayHouseFavorService;
import ti4.contest.replay.service.CombatReplayHousePhaseService;
import ti4.contest.replay.service.CombatReplayHouseService;

class CombatReplayHacanTradeConvoysServiceTest {

    private final CombatReplayHacanTradeConvoysRepository tradeConvoysRepository =
            mock(CombatReplayHacanTradeConvoysRepository.class);
    private final CombatReplayContestRepository contestRepository = mock(CombatReplayContestRepository.class);
    private final CombatReplayHouseScoreRepository houseScoreRepository = mock(CombatReplayHouseScoreRepository.class);
    private final CombatReplayHouseFavorService houseFavorService = mock(CombatReplayHouseFavorService.class);
    private final CombatReplayHouseAbilityUseRepository abilityUseRepository =
            mock(CombatReplayHouseAbilityUseRepository.class);
    private final CombatReplayHacanTradeConvoysVoteRepository tradeConvoysVoteRepository =
            mock(CombatReplayHacanTradeConvoysVoteRepository.class);
    private final CombatContestSettings settings = new CombatContestSettings();
    private final CombatReplayHouseAbilityVoteService voteService = new CombatReplayHouseAbilityVoteService(
            settings,
            mock(CombatReplayHouseAbilityUseRepository.class),
            mock(CombatReplayHouseAbilityVoteRepository.class),
            houseFavorService);
    private final CombatReplayHacanTradeConvoysService service = new CombatReplayHacanTradeConvoysService(
            settings,
            mock(CombatReplayHouseService.class),
            new CombatReplayHousePhaseService(settings, contestRepository),
            voteService,
            houseFavorService,
            mock(CombatCandidateRepository.class),
            contestRepository,
            abilityUseRepository,
            tradeConvoysRepository,
            tradeConvoysVoteRepository,
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
        when(houseFavorService.ledger(CombatReplayHouse.HACAN))
                .thenReturn(new CombatReplayHouseFavorService.FavorLedger(10, 0, 10));

        List<net.dv8tion.jda.api.components.buttons.Button> buttons =
                service.tradeConvoysButtonsForHouse(contest, CombatReplayHouse.NAALU);

        assertEquals(5, buttons.size());
        assertTrue(buttons.get(4).getLabel().startsWith("50 Favor: 36%"));
        assertFalse(buttons.get(0).isDisabled());
        assertTrue(buttons.get(1).isDisabled());
        assertTrue(buttons.get(2).isDisabled());
        assertTrue(buttons.get(3).isDisabled());
        assertTrue(buttons.get(4).isDisabled());
    }

    @Test
    void tradeConvoysLocksWithSingleVote() {
        CombatReplayContestEntity contest = contest(3L, 7L);
        CombatCandidateEntity candidate = candidate(7L);
        when(tradeConvoysRepository.findByContestId(contest.getId())).thenReturn(Optional.empty());
        when(tradeConvoysVoteRepository.findByContestId(contest.getId()))
                .thenReturn(List.of(vote(CombatReplayHouse.NAALU, 10, 10, "user-1")));
        when(houseFavorService.canAfford(CombatReplayHouse.HACAN, 10)).thenReturn(true);

        CombatReplayHacanTradeConvoysService.TradeConvoys tradeConvoys =
                service.lockTradeConvoysIfNeeded(contest, candidate);

        assertTrue(tradeConvoys.active());
        assertEquals(CombatReplayHouse.NAALU, tradeConvoys.targetHouse());
        assertEquals(10, tradeConvoys.favorCost());
        assertEquals(10, tradeConvoys.bonusPercent());
    }

    @Test
    void tradeConvoysTieBetweenFavorOptionsLocksLowerFavorValue() {
        CombatReplayContestEntity contest = contest(3L, 7L);
        CombatCandidateEntity candidate = candidate(7L);
        when(tradeConvoysRepository.findByContestId(contest.getId())).thenReturn(Optional.empty());
        when(tradeConvoysVoteRepository.findByContestId(contest.getId()))
                .thenReturn(List.of(
                        vote(CombatReplayHouse.NAALU, 50, 36, "user-1"),
                        vote(CombatReplayHouse.MENTAK, 10, 10, "user-2")));
        when(houseFavorService.canAfford(CombatReplayHouse.HACAN, 10)).thenReturn(true);

        service.lockTradeConvoysIfNeeded(contest, candidate);

        ArgumentCaptor<CombatReplayHacanTradeConvoysEntity> savedTradeConvoys =
                ArgumentCaptor.forClass(CombatReplayHacanTradeConvoysEntity.class);
        verify(tradeConvoysRepository).save(savedTradeConvoys.capture());
        assertEquals(10, savedTradeConvoys.getValue().getFavorCost());
        assertEquals(10, savedTradeConvoys.getValue().getPredictionBonus());
    }

    @Test
    void tradeConvoysLockedTargetMessageDoesNotRevealHacanPayout() {
        CombatReplayHacanTradeConvoysEntity convoy = convoy(1L, CombatReplayHouse.NAALU, 30, 15);

        String hacanMessage = service.hacanLockedTradeConvoysMessage(convoy);
        String targetMessage = service.targetLockedTradeConvoysMessage(convoy);

        assertTrue(hacanMessage.contains("will gain `15%`"));
        assertTrue(targetMessage.contains("Hacan sends `30 Favor` to Naalu Delegation"));
        assertFalse(targetMessage.contains("15%"));
        assertFalse(targetMessage.contains("earned points"));
        assertFalse(targetMessage.contains("will gain"));
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

    private CombatReplayContestEntity contest(Long contestId, Long candidateId) {
        CombatReplayContestEntity contest = new CombatReplayContestEntity();
        contest.setId(contestId);
        contest.setCandidateId(candidateId);
        return contest;
    }

    private CombatCandidateEntity candidate(Long candidateId) {
        CombatCandidateEntity candidate = new CombatCandidateEntity();
        candidate.setId(candidateId);
        return candidate;
    }

    private CombatReplayHacanTradeConvoysVoteEntity vote(
            CombatReplayHouse targetHouse, int favorCost, int bonusPercent, String discordUserId) {
        CombatReplayHacanTradeConvoysVoteEntity vote = new CombatReplayHacanTradeConvoysVoteEntity();
        vote.setContestId(3L);
        vote.setTargetHouse(targetHouse);
        vote.setFavorCost(favorCost);
        vote.setPredictionBonus(bonusPercent);
        vote.setDiscordUserId(discordUserId);
        vote.setDiscordUserName(discordUserId);
        vote.setVotedAt(LocalDateTime.now());
        return vote;
    }

    private CombatReplayHouseScoreEntity score(Long contestId) {
        CombatReplayHouseScoreEntity score = new CombatReplayHouseScoreEntity();
        score.setContestId(contestId);
        return score;
    }
}
