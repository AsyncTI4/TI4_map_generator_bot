package ti4.helpers;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import ti4.model.CombatModifierModel;
import ti4.model.NamedCombatModifierModel;
import ti4.model.UnitModel;
import ti4.service.combat.CombatRollType;

class CombatMessageHelperTest {

    @Test
    void extraDiceModifierWithoutDiceWordingGetsAnExplicitNotice() {
        var modifier = extraRollModifier(1, null);

        String shown = display(new NamedCombatModifierModel(modifier, "_Advanced Targeting Systems_"));

        assertThat(shown).contains("_Advanced Targeting Systems_ (+1 extra die)");
    }

    @Test
    void scaledExtraDiceModifierNamesWhatItScalesWith() {
        var modifier = extraRollModifier(1, "damaged_units");

        String shown = display(new NamedCombatModifierModel(modifier, "Survivalists"));

        assertThat(shown).contains("Survivalists (+1 extra die, scaling with damaged units)");
    }

    @Test
    void extraDiceModifierThatAlreadySaysDiceIsLeftAlone() {
        var modifier = extraRollModifier(1, null);

        String shown = display(new NamedCombatModifierModel(modifier, "1 of those units may roll 1 additional die."));

        assertThat(shown).isEqualTo("With modifiers: \n1 of those units may roll 1 additional die.\n");
    }

    @Test
    void nonDiceModifierIsLeftAlone() {
        var modifier = extraRollModifier(1, null);
        modifier.setType(Constants.COMBAT_MODIFIERS);

        String shown = display(new NamedCombatModifierModel(modifier, "Antimass Deflectors"));

        assertThat(shown).isEqualTo("With modifiers: \nAntimass Deflectors\n");
    }

    private static CombatModifierModel extraRollModifier(int value, String scaling) {
        var modifier = new CombatModifierModel();
        modifier.setAlias("test_extra_roll");
        modifier.setType(Constants.COMBAT_EXTRA_ROLLS);
        modifier.setValue(value);
        modifier.setValueScalingType(scaling);
        modifier.setScope("");
        modifier.setForCombatAbility(CombatRollType.combatround);
        return modifier;
    }

    private static String display(NamedCombatModifierModel modifier) {
        Map<UnitModel, Integer> noUnits = Map.of();
        return CombatMessageHelper.displayModifiers("With modifiers: \n", noUnits, List.of(modifier));
    }
}
