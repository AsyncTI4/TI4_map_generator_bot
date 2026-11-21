package ti4.service.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SelectedUnitExtraRollsTest {

    @Test
    void loneUnitKeepsAllItsExtraDice() {
        // Arc Secundus alone with Classified Weapons (+2) and Gravleash: all 4 dice belong to the flagship,
        // none spill into an empty "rest of units" segment.
        assertEquals(2, CombatRollService.selectedUnitExtraRolls(1, 2, 2));
    }

    @Test
    void classifiedWeaponsDiceStayOnTheSelectedUnitInAStack() {
        assertEquals(2, CombatRollService.selectedUnitExtraRolls(3, 2, 2));
    }

    @Test
    void stackWideExtraDiceGiveTheSelectedUnitAtMostOne() {
        assertEquals(1, CombatRollService.selectedUnitExtraRolls(3, 3, 0));
        assertEquals(3, CombatRollService.selectedUnitExtraRolls(3, 5, 2));
    }

    @Test
    void noExtraDice() {
        assertEquals(0, CombatRollService.selectedUnitExtraRolls(2, 0, 0));
    }
}
