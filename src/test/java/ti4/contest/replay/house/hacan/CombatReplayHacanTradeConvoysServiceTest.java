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
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.junit.jupiter.api.Test;
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
import ti4.contest.replay.service.CombatReplayInteractionResult;

class CombatReplayHacanTradeConvoysServiceTest {

    private final CombatReplayHacanTradeConvoysRepository tradeConvoysRepository =
            mock(CombatReplayHacanTradeConvoysRepository.class);
    private final CombatReplayContestRepository contestRepository = mock(CombatReplayContestRepository.class);
    private final CombatCandidateRepository candidateRepository = mock(CombatCandidateRepository.class);
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
            candidateRepository,
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
    void tradeConvoysVotingOnlyClosesAfterLaterCombatCompletes() {
        CombatCandidateEntity candidate = new CombatCandidateEntity();
        candidate.setId(7L);
        CombatReplayContestEntity contest = new CombatReplayContestEntity();
        contest.setId(3L);
        contest.setCandidateId(candidate.getId());
        when(tradeConvoysRepository.findByContestId(contest.getId())).thenReturn(Optional.empty());
        when(contestRepository.existsByIdGreaterThanAndReplayCompletedAtIsNotNull(contest.getId()))
                .thenReturn(false);

        assertFalse(service.shouldOfferVoting(contest, candidate));

        contest.setReplayCompletedAt(LocalDateTime.now());

        assertTrue(service.shouldOfferVoting(contest, candidate));

        when(contestRepository.existsByIdGreaterThanAndReplayCompletedAtIsNotNull(contest.getId()))
                .thenReturn(true);

        assertFalse(service.shouldOfferVoting(contest, candidate));
    }

    @Test
    void repostOpenTradeConvoysUsesPreviousWindowDuringCurrentPreview() {
        CombatReplayContestEntity previewContest = contest(4L, 8L);
        CombatCandidateEntity previewCandidate = candidate(8L);
        CombatReplayContestEntity previousContest = contest(3L, 7L);
        previousContest.setReplayCompletedAt(LocalDateTime.now().minusMinutes(10));
        CombatCandidateEntity previousCandidate = candidate(7L);

        when(contestRepository.findAllByOrderByIdDesc()).thenReturn(List.of(previewContest, previousContest));
        when(candidateRepository.findById(previewContest.getCandidateId())).thenReturn(Optional.of(previewCandidate));
        when(candidateRepository.findById(previousContest.getCandidateId())).thenReturn(Optional.of(previousCandidate));
        when(tradeConvoysRepository.findByContestId(previewContest.getId())).thenReturn(Optional.empty());
        when(tradeConvoysRepository.findByContestId(previousContest.getId())).thenReturn(Optional.empty());
        when(contestRepository.existsByIdGreaterThanAndReplayCompletedAtIsNotNull(previousContest.getId()))
                .thenReturn(false);

        assertTrue(service.repostOpenTradeConvoysVotingButtons());

        verify(tradeConvoysRepository).findByContestId(previousContest.getId());
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
        assertTrue(buttons.get(4).getLabel().startsWith("50 Favor: 31%"));
        assertFalse(buttons.get(0).isDisabled());
        assertTrue(buttons.get(1).isDisabled());
        assertTrue(buttons.get(2).isDisabled());
        assertTrue(buttons.get(3).isDisabled());
        assertTrue(buttons.get(4).isDisabled());
    }

    @Test
    void tradeConvoysVotesDoNotAutoLock() {
        CombatReplayContestEntity contest = contest(3L, 7L);
        CombatCandidateEntity candidate = candidate(7L);
        when(tradeConvoysRepository.findByContestId(contest.getId())).thenReturn(Optional.empty());
        when(tradeConvoysVoteRepository.findByContestId(contest.getId()))
                .thenReturn(List.of(vote(CombatReplayHouse.NAALU, 10, 9, "user-1")));
        when(houseFavorService.canAfford(CombatReplayHouse.HACAN, 10)).thenReturn(true);

        CombatReplayHacanTradeConvoysService.TradeConvoys tradeConvoys =
                service.lockTradeConvoysIfNeeded(contest, candidate);

        assertFalse(tradeConvoys.active());
        verify(tradeConvoysRepository, org.mockito.Mockito.never()).save(org.mockito.Mockito.any());
    }

    @Test
    void openingNewPostCombatWindowDoesNotLockPreviousTradeConvoysVotes() {
        CombatReplayContestEntity previousContest = contest(3L, 7L);
        previousContest.setReplayCompletedAt(LocalDateTime.now().minusMinutes(10));
        CombatReplayContestEntity currentContest = contest(4L, 8L);
        currentContest.setReplayCompletedAt(LocalDateTime.now());
        CombatCandidateEntity currentCandidate = candidate(8L);

        when(contestRepository.findFirstByIdLessThanOrderByIdDesc(currentContest.getId()))
                .thenReturn(Optional.of(previousContest));
        when(candidateRepository.findById(previousContest.getCandidateId())).thenReturn(Optional.of(candidate(7L)));
        when(tradeConvoysRepository.findByContestId(previousContest.getId())).thenReturn(Optional.empty());
        when(tradeConvoysVoteRepository.findByContestId(previousContest.getId()))
                .thenReturn(List.of(vote(CombatReplayHouse.NAALU, 10, 9, "user-1")));
        when(houseFavorService.canAfford(CombatReplayHouse.HACAN, 10)).thenReturn(true);

        service.postPostCombatTradeConvoysButtonsIfNeeded(currentContest, currentCandidate);

        verify(tradeConvoysRepository, org.mockito.Mockito.never()).save(org.mockito.Mockito.any());
    }

    @Test
    void tradeConvoysTieBetweenFavorOptionsDoesNotAutoLock() {
        CombatReplayContestEntity contest = contest(3L, 7L);
        CombatCandidateEntity candidate = candidate(7L);
        when(tradeConvoysRepository.findByContestId(contest.getId())).thenReturn(Optional.empty());
        when(tradeConvoysVoteRepository.findByContestId(contest.getId()))
                .thenReturn(List.of(
                        vote(CombatReplayHouse.NAALU, 50, 31, "user-1"),
                        vote(CombatReplayHouse.MENTAK, 10, 9, "user-2")));
        when(houseFavorService.canAfford(CombatReplayHouse.HACAN, 10)).thenReturn(true);

        service.lockTradeConvoysIfNeeded(contest, candidate);

        verify(tradeConvoysRepository, org.mockito.Mockito.never()).save(org.mockito.Mockito.any());
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

    @Test
    void sendNowImmediatelyLocksAndCreditsFavorInDevMode() {
        settings.getRuntime().setDevMode(true);
        CombatReplayContestEntity contest = contest(3L, 7L);
        contest.setReplayCompletedAt(LocalDateTime.now());
        CombatCandidateEntity candidate = candidate(7L);
        ButtonInteractionEvent event = mock(ButtonInteractionEvent.class);
        User user = mock(User.class);
        CombatReplayHouseScoreEntity targetScore = score(contest.getId());
        targetScore.setHouse(CombatReplayHouse.NAALU);
        targetScore.setFavorPoints(5);

        when(event.getUser()).thenReturn(user);
        when(user.getId()).thenReturn("user-1");
        when(user.getEffectiveName()).thenReturn("User 1");
        when(contestRepository.findById(contest.getId())).thenReturn(Optional.of(contest));
        when(contestRepository.existsByIdGreaterThanAndReplayCompletedAtIsNotNull(contest.getId()))
                .thenReturn(false);
        when(candidateRepository.findById(candidate.getId())).thenReturn(Optional.of(candidate));
        when(tradeConvoysRepository.findByContestId(contest.getId())).thenReturn(Optional.empty());
        when(houseFavorService.canAfford(CombatReplayHouse.HACAN, 10)).thenReturn(true);
        when(houseScoreRepository.findByContestIdAndHouse(contest.getId(), CombatReplayHouse.NAALU))
                .thenReturn(Optional.of(targetScore));

        CombatReplayInteractionResult result = service.sendTradeConvoysNow(
                event,
                new CombatReplayHacanTradeConvoysService.ParsedTradeConvoysButton(
                        contest.getId(), CombatReplayHouse.NAALU, 10, 9));

        assertTrue(result.accepted());
        assertEquals(15, targetScore.getFavorPoints());
        verify(tradeConvoysRepository)
                .save(org.mockito.ArgumentMatchers.any(CombatReplayHacanTradeConvoysEntity.class));
        verify(houseScoreRepository).saveAndFlush(targetScore);
    }

    @Test
    void tradeConvoysParserAcceptsSendNowButtons() {
        CombatReplayHacanTradeConvoysService.ParsedTradeConvoysButton parsed =
                CombatReplayHacanTradeConvoysService.parseButtonId(
                        CombatReplayHacanTradeConvoysService.HACAN_TRADE_CONVOYS_SEND_NOW_PREFIX + "3~NAALU~10~10");

        assertEquals(3L, parsed.contestId());
        assertEquals(CombatReplayHouse.NAALU, parsed.targetHouse());
        assertEquals(10, parsed.favorCost());
        assertEquals(10, parsed.bonusPercent());
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
