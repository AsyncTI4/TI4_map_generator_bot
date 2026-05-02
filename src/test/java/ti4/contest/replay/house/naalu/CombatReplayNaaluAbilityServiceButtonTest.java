package ti4.contest.replay.house.naalu;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import net.dv8tion.jda.api.components.buttons.Button;
import org.junit.jupiter.api.Test;
import ti4.contest.replay.core.CombatContestSettings;
import ti4.contest.replay.core.CombatReplayHouse;
import ti4.contest.replay.dispatch.ReplayDispatchSerializer;
import ti4.contest.replay.repository.CombatCandidateEventRepository;
import ti4.contest.replay.repository.CombatCandidateRepository;
import ti4.contest.replay.repository.CombatReplayContestRepository;
import ti4.contest.replay.repository.CombatReplayHouseAbilityUseRepository;
import ti4.contest.replay.repository.CombatReplayHouseAbilityVoteRepository;
import ti4.contest.replay.service.CombatReplayHouseFavorService;
import ti4.contest.replay.service.CombatReplayHouseService;
import ti4.discord.interactions.buttons.Buttons;

class CombatReplayNaaluAbilityServiceButtonTest {

    private final CombatReplayHouseFavorService houseFavorService = mock(CombatReplayHouseFavorService.class);
    private final CombatReplayNaaluAbilityService service = new CombatReplayNaaluAbilityService(
            new CombatContestSettings(),
            mock(CombatReplayContestRepository.class),
            mock(CombatCandidateRepository.class),
            mock(CombatCandidateEventRepository.class),
            mock(CombatReplayHouseAbilityUseRepository.class),
            mock(CombatReplayHouseAbilityVoteRepository.class),
            houseFavorService,
            mock(CombatReplayHouseService.class),
            new ReplayDispatchSerializer());

    @Test
    void visionButtonsStayVisibleButDisableUnaffordableFavorCosts() {
        when(houseFavorService.canAfford(CombatReplayHouse.NAALU, 30)).thenReturn(true);
        when(houseFavorService.canAfford(CombatReplayHouse.NAALU, 50)).thenReturn(false);

        Button affordable = service.abilityButton(Buttons.blue("naalu_affordable", "Affordable"), 30);
        Button unaffordable = service.abilityButton(Buttons.blue("naalu_unaffordable", "Unaffordable"), 50);

        assertFalse(affordable.isDisabled());
        assertTrue(unaffordable.isDisabled());
    }
}
