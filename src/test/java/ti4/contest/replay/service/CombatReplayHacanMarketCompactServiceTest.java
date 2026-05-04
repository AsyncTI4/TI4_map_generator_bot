package ti4.contest.replay.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import ti4.contest.replay.core.CombatContestSettings;
import ti4.contest.replay.core.CombatReplayHouse;
import ti4.contest.replay.core.CombatSideBetType;
import ti4.contest.replay.entities.CombatContestSideBetEntity;
import ti4.contest.replay.entities.CombatReplayHacanSubsidyEntity;
import ti4.contest.replay.house.hacan.CombatReplayHacanMarketCompactService;
import ti4.contest.replay.repository.CombatCandidateRepository;
import ti4.contest.replay.repository.CombatReplayContestRepository;
import ti4.contest.replay.repository.CombatReplayHacanMarketCompactDecisionRepository;
import ti4.contest.replay.repository.CombatReplayHacanSubsidyRepository;
import ti4.contest.replay.repository.CombatReplayHacanSubsidyVoteRepository;

class CombatReplayHacanMarketCompactServiceTest {

    private final CombatReplayHacanSubsidyRepository marketRepository = mock(CombatReplayHacanSubsidyRepository.class);
    private final CombatReplayHouseService houseService = mock(CombatReplayHouseService.class);
    private final CombatReplayHacanMarketCompactService service = new CombatReplayHacanMarketCompactService(
            new CombatContestSettings(),
            mock(CombatReplayContestRepository.class),
            mock(CombatCandidateRepository.class),
            mock(CombatReplayHacanSubsidyVoteRepository.class),
            marketRepository,
            mock(CombatReplayHacanMarketCompactDecisionRepository.class),
            mock(CombatReplayHouseAbilityVoteService.class),
            houseService,
            mock(CombatReplayHousePhaseService.class),
            mock(CombatReplaySideBetPayoutService.class),
            mock(CombatSideBetAvailabilityService.class));

    @Test
    void marketMakerPointsCountEachNonHacanMarkedSideBetTaken() {
        when(marketRepository.findByContestId(1L)).thenReturn(List.of(markedBet()));
        when(houseService.houseForUser("hacan-user")).thenReturn(CombatReplayHouse.HACAN);
        when(houseService.houseForUser("naalu-user")).thenReturn(CombatReplayHouse.NAALU);
        when(houseService.houseForUser("mentak-user")).thenReturn(CombatReplayHouse.MENTAK);

        int points = service.marketMakerPoints(
                1L,
                List.of(
                        sideBet("hacan-user", CombatSideBetType.ROUND_ONE_WHIFF, "sol"),
                        sideBet("naalu-user", CombatSideBetType.ROUND_ONE_WHIFF, "sol"),
                        sideBet("naalu-user", CombatSideBetType.ROUND_ONE_WHIFF, "sol"),
                        sideBet("mentak-user", CombatSideBetType.ROUND_ONE_WHIFF, "sol"),
                        sideBet("mentak-user", CombatSideBetType.ROUND_ONE_SLAM, "sol")));

        assertEquals(3, points);
    }

    private CombatReplayHacanSubsidyEntity markedBet() {
        CombatReplayHacanSubsidyEntity markedBet = new CombatReplayHacanSubsidyEntity();
        markedBet.setContestId(1L);
        markedBet.setBetType(CombatSideBetType.ROUND_ONE_WHIFF);
        markedBet.setTargetFaction("sol");
        return markedBet;
    }

    private CombatContestSideBetEntity sideBet(String userId, CombatSideBetType betType, String targetFaction) {
        CombatContestSideBetEntity sideBet = new CombatContestSideBetEntity();
        sideBet.setContestId(1L);
        sideBet.setDiscordUserId(userId);
        sideBet.setBetType(betType);
        sideBet.setTargetFaction(targetFaction);
        return sideBet;
    }
}
