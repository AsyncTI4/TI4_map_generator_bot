package ti4.ai.tactical;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import ti4.ai.eval.CombatOdds.Combatant;
import ti4.helpers.Units.UnitType;

// The agents of Sol and Letnev add a die to the unit with the best chance to hit, and the Sol agent favours a mech.
class ExtraDieTargetTest {

    private static Combatant unit(UnitType type, int hitsOn, int dice, double cost) {
        return new Combatant(hitsOn, dice, false, cost, type);
    }

    @Test
    void picksTheShipWithTheLowestHitNumberAfterModifiers() {
        List<Combatant> fleet = List.of(
                unit(UnitType.Cruiser, 7, 1, 2),
                unit(UnitType.Dreadnought, 5, 1, 4),
                unit(UnitType.Destroyer, 8, 1, 1));

        assertThat(ExtraDieTarget.choose(fleet, false)).hasValue(1);
    }

    @Test
    void breaksATieOnHitNumberWithTheShipRollingMoreDice() {
        List<Combatant> tied = List.of(unit(UnitType.Dreadnought, 5, 1, 4), unit(UnitType.Flagship, 5, 2, 8));

        assertThat(ExtraDieTarget.choose(tied, false)).hasValue(1);
    }

    @Test
    void breaksARemainingTieWithTheCheaperShip() {
        List<Combatant> fleet = List.of(unit(UnitType.Dreadnought, 5, 1, 4), unit(UnitType.Cruiser, 5, 1, 2));

        assertThat(ExtraDieTarget.choose(fleet, false)).hasValue(1);
    }

    @Test
    void ignoresUnitsThatRollNothing() {
        List<Combatant> fleet = List.of(unit(UnitType.Carrier, 1, 0, 3), unit(UnitType.Cruiser, 7, 1, 2));

        assertThat(ExtraDieTarget.choose(fleet, false)).hasValue(1);
    }

    @Test
    void choosesNothingWhenNothingRolls() {
        assertThat(ExtraDieTarget.choose(List.of(unit(UnitType.Carrier, 1, 0, 3)), false))
                .isEmpty();
    }

    @Test
    void groundCombatPrefersAMechOnATie() {
        List<Combatant> troops = List.of(unit(UnitType.Infantry, 6, 1, 0.5), unit(UnitType.Mech, 6, 1, 2));

        assertThat(ExtraDieTarget.choose(troops, true)).hasValue(1);
    }

    @Test
    void groundCombatPrefersAMechOnANearTie() {
        List<Combatant> troops = List.of(unit(UnitType.Infantry, 6, 1, 0.5), unit(UnitType.Mech, 7, 1, 2));

        assertThat(ExtraDieTarget.choose(troops, true)).hasValue(1);
    }

    @Test
    void groundCombatTakesTheBetterInfantryWhenTheMechIsFarWorse() {
        List<Combatant> troops = List.of(unit(UnitType.Infantry, 5, 1, 0.5), unit(UnitType.Mech, 7, 1, 2));

        assertThat(ExtraDieTarget.choose(troops, true)).hasValue(0);
    }

    @Test
    void spaceCombatNeverGivesAMechSpecialTreatment() {
        List<Combatant> troops = List.of(unit(UnitType.Infantry, 6, 1, 0.5), unit(UnitType.Mech, 7, 1, 2));

        assertThat(ExtraDieTarget.choose(troops, false)).hasValue(0);
    }
}
