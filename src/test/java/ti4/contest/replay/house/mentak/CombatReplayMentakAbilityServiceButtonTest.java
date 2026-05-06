package ti4.contest.replay.house.mentak;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import net.dv8tion.jda.api.components.buttons.Button;
import org.junit.jupiter.api.Test;
import ti4.contest.replay.core.CombatContestSettings;
import ti4.contest.replay.core.CombatReplayHouse;
import ti4.contest.replay.entities.CombatCandidateEntity;
import ti4.contest.replay.entities.CombatReplayHouseAbilityVoteEntity;
import ti4.contest.replay.repository.CombatCandidateRepository;
import ti4.contest.replay.repository.CombatReplayHouseAbilityUseRepository;
import ti4.contest.replay.service.CombatReplayHouseAbilityVoteService;
import ti4.contest.replay.service.CombatReplayHouseFavorService;
import ti4.contest.replay.service.CombatReplayHousePhaseService;
import ti4.contest.replay.service.CombatReplayHouseService;
import ti4.helpers.Units.UnitType;

class CombatReplayMentakAbilityServiceButtonTest {

    private final CombatContestSettings settings = new CombatContestSettings();
    private final CombatReplayHouseFavorService houseFavorService = mock(CombatReplayHouseFavorService.class);
    private final CombatReplayHouseAbilityVoteService voteService = mock(CombatReplayHouseAbilityVoteService.class);
    private final CombatReplayMentakAbilityService service = new CombatReplayMentakAbilityService(
            settings,
            mock(CombatCandidateRepository.class),
            mock(CombatReplayHouseAbilityUseRepository.class),
            houseFavorService,
            voteService,
            mock(CombatReplayHousePhaseService.class),
            mock(CombatReplayHouseService.class));

    @Test
    void quantityButtonsShowTotalCostAndDisableUnaffordableCounts() {
        CombatCandidateEntity candidate = new CombatCandidateEntity();
        candidate.setId(7L);
        when(houseFavorService.canAfford(eq(CombatReplayHouse.MENTAK), eq(30))).thenReturn(true);
        when(houseFavorService.canAfford(eq(CombatReplayHouse.MENTAK), eq(53))).thenReturn(false);
        when(houseFavorService.canAfford(eq(CombatReplayHouse.MENTAK), eq(76))).thenReturn(false);
        when(houseFavorService.canAfford(eq(CombatReplayHouse.MENTAK), eq(99))).thenReturn(false);
        when(houseFavorService.canAfford(eq(CombatReplayHouse.MENTAK), eq(122))).thenReturn(false);

        List<Button> buttons = service.quantityButtons(candidate, "naalu", UnitType.Cruiser);

        assertEquals("1x (-30 Favor)", buttons.getFirst().getLabel());
        assertFalse(buttons.getFirst().isDisabled());
        assertEquals("2x (-53 Favor)", buttons.get(1).getLabel());
        assertTrue(buttons.get(1).isDisabled());
    }

    @Test
    void mentakVoteThresholdPoolsCountsThenLowestTiedCountWins() {
        when(voteService.votesFor(7L, CombatReplayHouse.MENTAK))
                .thenReturn(List.of(
                        vote("mentak_Destroyer_1", "1"),
                        vote("mentak_Destroyer_1", "2"),
                        vote("mentak_Destroyer_2", "3"),
                        vote("mentak_Destroyer_2", "4"),
                        vote("mentak_Destroyer_3", "5")));

        CombatReplayHouseAbilityVoteService.WinningVote winningVote = service.winningMentakVote(7L);

        assertEquals("mentak_Destroyer_1", winningVote.optionKey());
        assertEquals(5, winningVote.voteCount());
    }

    @Test
    void mentakVoteTiedUnitTypesChooseMostExpensiveUnit() {
        when(voteService.votesFor(7L, CombatReplayHouse.MENTAK))
                .thenReturn(List.of(
                        vote("mentak_Destroyer_1", "1"),
                        vote("mentak_Destroyer_2", "2"),
                        vote("mentak_Destroyer_3", "3"),
                        vote("mentak_Warsun_1", "4"),
                        vote("mentak_Warsun_2", "5"),
                        vote("mentak_Warsun_3", "6")));

        CombatReplayHouseAbilityVoteService.WinningVote winningVote = service.winningMentakVote(7L);

        assertEquals("mentak_Warsun_1", winningVote.optionKey());
        assertEquals(3, winningVote.voteCount());
    }

    @Test
    void mentakVoteThresholdRequiresOnlyTwoPooledUnitTypeVotes() {
        when(voteService.votesFor(7L, CombatReplayHouse.MENTAK))
                .thenReturn(List.of(vote("mentak_Cruiser_1", "1"), vote("mentak_Cruiser_2", "2")));

        CombatReplayHouseAbilityVoteService.WinningVote winningVote = service.winningMentakVote(7L);

        assertEquals("mentak_Cruiser_1", winningVote.optionKey());
        assertEquals(2, winningVote.voteCount());
    }

    private CombatReplayHouseAbilityVoteEntity vote(String optionKey, String voterId) {
        CombatReplayHouseAbilityVoteEntity vote = new CombatReplayHouseAbilityVoteEntity();
        vote.setCandidateId(7L);
        vote.setHouse(CombatReplayHouse.MENTAK);
        vote.setOptionKey(optionKey);
        vote.setDiscordUserId(voterId);
        vote.setDiscordUserName("Voter " + voterId);
        return vote;
    }
}
