package ti4.ai.explore;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.game.Planet;
import ti4.game.Tile;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitType;
import ti4.model.MapPositionMapper;
import ti4.testUtils.BaseTi4Test;

// The Ionian Fuel Refinery (Tempesta) is an exhaust ability: +1 move to one ship, once per round. Two carriers that
// are each one system short of an open planet cannot both use it, so only one of those launches happens this round.
// Two single-planet systems sit two systems from Nekro's home down separate lanes; the carriers move 1.
class TempestaExpansionOutlookTest extends BaseTi4Test {

    private AiTestGame test;
    private Planet holding;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        Tile home = test.nekroHome();
        test.nekro.setTacticalCC(3);
        laneEndingIn("19");
        laneEndingIn("20");
        test.units(home, "space", test.nekro, UnitType.Carrier, 2);
        test.units(home, "mordaiii", test.nekro, UnitType.Infantry, 2);
        holding = (Planet) home.getUnitHolders().get("mordaiii");
    }

    private void laneEndingIn(String planetSystem) {
        String position = AiTestGame.HOME;
        for (int step = 0; step < 2; step++) {
            String previous = position;
            position = MapPositionMapper.getAdjacentTilePositions(previous).stream()
                    .filter(candidate -> !"x".equals(candidate))
                    .filter(candidate -> test.game.getTileByPosition(candidate) == null)
                    .filter(candidate -> MapPositionMapper.getAdjacentTilePositions(candidate).stream()
                            .noneMatch(next -> !next.equals(previous) && test.game.getTileByPosition(next) != null))
                    .findFirst()
                    .orElseThrow();
            test.place(step == 0 ? "46" : planetSystem, position);
        }
    }

    // Without any +1 neither claim is in reach, so the infantry at home has no launch to wait for.
    @Test
    void noBoostNoLaunch() {
        assertThat(new ExpansionOutlook(test.game, test.nekro).need(holding)).isZero();
    }

    // One Tempesta boost funds one launch; the two infantry cover it (one stays on the holding planet), so neither
    // is short. Counting Tempesta for both carriers would have left the second claim unfunded.
    @Test
    void tempestaFundsOnlyOneLaunchPerRound() {
        test.nekro.addPlanet("tempesta");

        assertThat(new ExpansionOutlook(test.game, test.nekro).need(holding)).isZero();
    }

    // The Tempesta launch is real: with a single infantry (which must stay on the holding planet) it is unfunded.
    @Test
    void theTempestaLaunchNeedsAnInfantry() {
        test.nekro.addPlanet("tempesta");
        holding.removeUnit(Units.getUnitKey(UnitType.Infantry, test.nekro.getColor()), 1);

        assertThat(new ExpansionOutlook(test.game, test.nekro).need(holding)).isPositive();
    }

    // Gravity Drive comes back every tactical action, so both launches reach and the second claim needs the infantry.
    @Test
    void gravityDriveFundsEveryLaunch() {
        test.nekro.addTech("gd");

        assertThat(new ExpansionOutlook(test.game, test.nekro).need(holding)).isPositive();
    }
}
