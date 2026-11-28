package ti4.ai.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import ti4.ai.eval.CombatOdds.Combatant;
import ti4.helpers.Units.UnitType;

class CombatantLossOrderTest {

    private static final Combatant DREADNOUGHT = new Combatant(5, 1, true, 4, UnitType.Dreadnought);
    private static final Combatant CARRIER = new Combatant(9, 1, false, 3, UnitType.Carrier);
    private static final Combatant DESTROYER = new Combatant(9, 1, false, 1, UnitType.Destroyer);

    @Test
    void hitsGoToTheCheapestShipFirst() {
        // One sustain, then the destroyer, then the carrier: the dreadnought is what is left after three hits.
        assertThat(CombatOdds.survivors(List.of(DREADNOUGHT, CARRIER, DESTROYER), 3))
                .extracting(Combatant::type)
                .containsExactly(UnitType.Dreadnought);
    }

    @Test
    void aGuardedCarrierOutlastsTheDreadnought() {
        // The attacker keeps the carrier its landing needs and takes the hit on the dreadnought instead.
        assertThat(CombatOdds.survivors(List.of(DREADNOUGHT, CARRIER.guarding(), DESTROYER), 3))
                .extracting(Combatant::type)
                .containsExactly(UnitType.Carrier);
    }

    @Test
    void guardingSurvivesOtherChangesToTheUnit() {
        assertThat(CARRIER.guarding().rolling(8, 2).guarded()).isTrue();
        assertThat(DREADNOUGHT.guarding().withoutSustain().guarded()).isTrue();
    }
}
