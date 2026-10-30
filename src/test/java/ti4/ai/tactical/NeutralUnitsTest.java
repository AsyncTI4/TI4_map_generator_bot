package ti4.ai.tactical;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.game.Player;
import ti4.helpers.Units.UnitType;
import ti4.model.UnitModel;
import ti4.testUtils.BaseTi4Test;

// The odds read the neutral player's own unit models, so they must match the neutral unit reference card:
// combat values, sustain damage, anti-fighter barrage and space cannon.
class NeutralUnitsTest extends BaseTi4Test {

    private static final Map<UnitType, Integer> COMBAT = Map.of(
            UnitType.Dreadnought, 5,
            UnitType.Carrier, 9,
            UnitType.Cruiser, 6,
            UnitType.Destroyer, 8,
            UnitType.Fighter, 8,
            UnitType.Infantry, 8,
            UnitType.Mech, 6);

    private final Player neutral = new AiTestGame().game.getNeutral();

    private UnitModel model(UnitType type) {
        return neutral.getUnitByType(type);
    }

    @Test
    void combatValuesFollowTheNeutralUnitReference() {
        COMBAT.forEach((type, value) ->
                assertThat(model(type).getCombatHitsOn()).as(type.name()).isEqualTo(value));
    }

    @Test
    void dreadnoughtsAndMechsSustainDamage() {
        assertThat(model(UnitType.Dreadnought).getSustainDamage()).isTrue();
        assertThat(model(UnitType.Mech).getSustainDamage()).isTrue();
        assertThat(model(UnitType.Cruiser).getSustainDamage()).isFalse();
    }

    @Test
    void destroyersBarrageAtSixWithThreeDice() {
        UnitModel destroyer = model(UnitType.Destroyer);

        assertThat(destroyer.getAfbDieCount(neutral)).isEqualTo(3);
        assertThat(destroyer.getAfbHitsOn(neutral)).isEqualTo(6);
    }

    @Test
    void theDreadnoughtBombardsAtFive() {
        assertThat(model(UnitType.Dreadnought).getBombardHitsOn()).isEqualTo(5);
    }
}
