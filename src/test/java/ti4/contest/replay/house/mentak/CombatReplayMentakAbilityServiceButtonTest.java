package ti4.contest.replay.house.mentak;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import net.dv8tion.jda.api.components.buttons.Button;
import org.junit.jupiter.api.Test;
import ti4.contest.replay.core.CombatContestSettings;
import ti4.contest.replay.core.CombatReplayHouse;
import ti4.contest.replay.entities.CombatCandidateEntity;
import ti4.contest.replay.repository.CombatCandidateRepository;
import ti4.contest.replay.repository.CombatReplayHouseAbilityUseRepository;
import ti4.contest.replay.repository.CombatReplayHouseAbilityVoteRepository;
import ti4.contest.replay.service.CombatReplayHouseFavorService;
import ti4.contest.replay.service.CombatReplayHouseService;
import ti4.helpers.Units.UnitType;

class CombatReplayMentakAbilityServiceButtonTest {

    private final CombatContestSettings settings = new CombatContestSettings();
    private final CombatReplayHouseFavorService houseFavorService = mock(CombatReplayHouseFavorService.class);
    private final CombatReplayMentakAbilityService service = new CombatReplayMentakAbilityService(
            settings,
            mock(CombatCandidateRepository.class),
            mock(CombatReplayHouseAbilityUseRepository.class),
            mock(CombatReplayHouseAbilityVoteRepository.class),
            houseFavorService,
            mock(CombatReplayHouseService.class));

    @Test
    void decoyButtonsStayVisibleButDisableUnaffordableFavorCosts() {
        CombatCandidateEntity candidate = new CombatCandidateEntity();
        candidate.setId(7L);
        int warSunCost = settings.getHouseAbilities().getMentak().getWarSunDecoyFavorCost();
        when(houseFavorService.canAfford(eq(CombatReplayHouse.MENTAK), anyInt()))
                .thenReturn(true);
        when(houseFavorService.canAfford(CombatReplayHouse.MENTAK, warSunCost)).thenReturn(false);

        Button unaffordable = service.decoyButton(candidate, "naalu", UnitType.Warsun);

        assertNotNull(unaffordable);
        assertTrue(unaffordable.isDisabled());
    }
}
