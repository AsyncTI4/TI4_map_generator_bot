package ti4.ai.tactical;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

class PrecisionRulesTest extends BaseTi4Test {

    private static final double EXACT = 1e-9;

    private AiTestGame test;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.nekroHome();
    }

    // A destroyer rolls two barrage dice that hit on 9 (a fifth each). Two of them roll four dice: at least one hit
    // is 1 - 0.8^4. Nothing is cleared when there are no fighters to hit.
    @Test
    void twoDestroyersClearASingleFighterAboutThreeTimesInFive() {
        Map<UnitType, Integer> ships = Map.of(UnitType.Destroyer, 2);

        assertThat(PrecisionRules.chanceToClear(test.nekro, ships, 1)).isCloseTo(1 - Math.pow(0.8, 4), within(EXACT));
        assertThat(PrecisionRules.chanceToClear(test.nekro, ships, 0)).isZero();
    }

    // Three destroyers roll six dice: two hits or more is 1 - 0.8^6 - 6 * 0.2 * 0.8^5.
    @Test
    void moreFightersNeedMoreHits() {
        Map<UnitType, Integer> ships = Map.of(UnitType.Destroyer, 3);

        assertThat(PrecisionRules.chanceToClear(test.nekro, ships, 2))
                .isCloseTo(1 - Math.pow(0.8, 6) - 6 * 0.2 * Math.pow(0.8, 5), within(EXACT));
    }

    // Destroyer II rolls three dice that hit on 6 (half each): two ships roll six dice, at least one hit is 1 - 0.5^6.
    @Test
    void destroyerIiBarragesFarHarder() {
        test.nekro.addTech("ps");
        test.nekro.addTech("da");
        test.nekro.addTech("dd2");

        assertThat(PrecisionRules.chanceToClear(test.nekro, Map.of(UnitType.Destroyer, 2), 1))
                .isCloseTo(1 - Math.pow(0.5, 6), within(EXACT));
    }

    // Ships without anti-fighter barrage add no dice.
    @Test
    void shipsWithoutBarrageRollNothing() {
        assertThat(PrecisionRules.chanceToClear(test.nekro, Map.of(UnitType.Cruiser, 3, UnitType.Carrier, 2), 1))
                .isZero();
    }

    // A dock faces fighters when a destroyer could reach a system holding some in one trip.
    @Test
    void aDockFacesFightersWithinOneDestroyerMove() {
        Tile home = test.game.getTileByPosition(AiTestGame.HOME);
        Tile near = test.place("46", AiTestGame.neighbourOf(AiTestGame.HOME));
        assertThat(PrecisionRules.facesFighters(test.game, test.nekro, home)).isFalse();

        test.units(near, "space", test.sol, UnitType.Fighter, 2);

        assertThat(PrecisionRules.facesFighters(test.game, test.nekro, home)).isTrue();
    }
}
