package ti4.ai.trade;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.tactical.GroundDrops;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

// The watch games showed Sol never selling Military Support: its neighbours had no invasion, defence or Occupy the
// Fringe need for infantry, so they did not want it. A seat that would otherwise build infantry to keep its spare
// ground forces up (4 movable at home, as production plans) also wants it: each of the 2 infantry filling that gap is
// worth 1 (half for having it, half for the build it saves), 2 in all against the 1 trade good price.
class MilitarySupportDemandTest extends BaseTi4Test {

    @Test
    void aSeatShortOfSpareInfantryWantsIt() {
        AiTestGame test = new AiTestGame();
        Tile home = test.nekroHome();
        test.units(home, "space", test.nekro, UnitType.Carrier, 1);

        assertThat(GroundDrops.spareShortfall(test.game, test.nekro)).isGreaterThanOrEqualTo(2);
        assertThat(MilitarySupport.receivedBy(test.game, test.nekro)).isGreaterThanOrEqualTo(2.0);
        assertThat(MilitarySupport.wantsInfantry(test.game, test.nekro)).isTrue();
    }

    @Test
    void aSeatWithItsSpareInfantryDoesNotNeedIt() {
        AiTestGame test = new AiTestGame();
        Tile home = test.nekroHome();
        test.units(home, "space", test.nekro, UnitType.Carrier, 1);
        test.units(home, "mordaiii", test.nekro, UnitType.Infantry, 6);

        assertThat(GroundDrops.spareShortfall(test.game, test.nekro)).isZero();
    }
}
