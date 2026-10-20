package ti4.ai.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.scoring.ObjectiveValue;
import ti4.game.Planet;
import ti4.testUtils.BaseTi4Test;

// The Shard of the Throne changes hands, a point with it, when someone takes a legendary planet its holder controls or
// a planet in the holder's home system.
class ShardPrizeTest extends BaseTi4Test {

    private static final double EXACT = 1e-9;

    private AiTestGame test;
    private Planet jord;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.nekroHome();
        test.solHomeBeside(AiTestGame.neighbourOf(AiTestGame.HOME));
        jord = (Planet) test.game.getUnitHolderFromPlanet("jord");
    }

    @Test
    void aPlanetInTheHoldersHomeSystemCarriesThePoint() {
        test.sol.addRelic("shard");

        assertThat(ShardPrize.stake(test.game, test.sol, jord))
                .isCloseTo(ObjectiveValue.VICTORY_POINT_VALUE, within(EXACT));
        assertThat(ShardPrize.gain(test.game, test.nekro, jord))
                .isCloseTo(ObjectiveValue.VICTORY_POINT_VALUE, within(EXACT));
    }

    @Test
    void nothingIsAtStakeWithoutTheShard() {
        assertThat(ShardPrize.stake(test.game, test.sol, jord)).isZero();
        assertThat(ShardPrize.gain(test.game, test.nekro, jord)).isZero();
    }

    @Test
    void theHolderGainsNothingFromItsOwnPlanet() {
        test.sol.addRelic("shard");

        assertThat(ShardPrize.gain(test.game, test.sol, jord)).isZero();
    }

    @Test
    void aPlanetOutsideTheHomeSystemOnlyCountsWhenItIsLegendary() {
        test.sol.addRelic("shard");
        test.place("22", "302");
        test.sol.addPlanet("tarmann");
        Planet tarmann = (Planet) test.game.getUnitHolderFromPlanet("tarmann");

        assertThat(ShardPrize.gain(test.game, test.nekro, tarmann)).isZero();
    }
}
