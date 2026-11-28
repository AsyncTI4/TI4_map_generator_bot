package ti4.ai.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

// Fighter II: fighters beyond capacity count against the fleet pool (LRR 16.3, ships ruling I). Hybrid Crystal
// Fighter II counts each excess fighter as half a ship.
class FighterWingsTest extends BaseTi4Test {

    private final AiTestGame test = new AiTestGame();
    private final Game game = test.game;
    private final Player seat = test.nekro;

    // A carrier holds 4; the other two Fighter IIs each take a fleet slot beside it.
    @Test
    void excessFighterIIsCostOneSlotEach() {
        seat.removeOwnedUnitByID("fighter");
        seat.addOwnedUnitByID("fighter2");
        Map<UnitType, Integer> space = Map.of(UnitType.Carrier, 1, UnitType.Fighter, 6);

        assertThat(FighterWings.flies(game, seat)).isTrue();
        assertThat(FighterWings.room(seat, space, 0, false)).isEqualTo(4);
        assertThat(FighterWings.fleetSlots(game, seat, space, 0, false)).isEqualTo(3);
        assertThat(FighterWings.fleetSlots(game, seat, space, 2, false)).isEqualTo(5);
        assertThat(FighterWings.fleetSlots(game, seat, space, 0, true)).isEqualTo(1);
        assertThat(FighterWings.fitsCapacity(game, seat, space, 0, false)).isTrue();
    }

    // Three excess hybrid fighters round up to two slots.
    @Test
    void hybridCrystalFightersCostHalfASlot() {
        seat.removeOwnedUnitByID("fighter");
        seat.addOwnedUnitByID("naalu_fighter2");
        seat.addTech("hcf2");
        Map<UnitType, Integer> space = Map.of(UnitType.Carrier, 1, UnitType.Fighter, 7);

        assertThat(FighterWings.perFleetSlot(seat)).isEqualTo(2);
        assertThat(FighterWings.fleetSlots(game, seat, space, 0, false)).isEqualTo(3);
    }

    // Fighter Is beyond capacity are illegal outright; they never spend fleet slots.
    @Test
    void excessFightersAreIllegalWithoutFighterII() {
        Map<UnitType, Integer> space = Map.of(UnitType.Carrier, 1, UnitType.Fighter, 5);

        assertThat(FighterWings.flies(game, seat)).isFalse();
        assertThat(FighterWings.fitsCapacity(game, seat, space, 0, false)).isFalse();
        assertThat(FighterWings.fleetSlots(game, seat, space, 0, false)).isEqualTo(1);
        assertThat(FighterWings.fitsCapacity(game, seat, space, 0, true)).isTrue();
    }
}
