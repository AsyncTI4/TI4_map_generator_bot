package ti4.ai.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.tactical.ProductionPlanner;
import ti4.game.Tile;
import ti4.model.Mapper;
import ti4.testUtils.BaseTi4Test;

// Live game aiplay1, round 2: Hacan took Thunder's Edge Construction for "produce at a space dock" (+11.6), the whole
// value of the build. The seat could build at that dock with a tactical action anyway, so the card's production only
// saves the token, plus the better of moving the new units out this round or keeping the system free for a second
// build.
class ConstructionProductionValueTest extends BaseTi4Test {

    private AiTestGame test;
    private Tile home;
    private long modified = 1;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        home = test.nekroHome();
        test.game.setStrategyCardSet("te");
        test.game.setVp(10);
        test.nekro.setTg(10);
        test.nekro.setTacticalCC(3);
        changed();
    }

    private void changed() {
        test.game.setLastModifiedDate(modified++);
    }

    private PlanInputs inputs() {
        return PlanInputs.of(test.game, test.nekro, PlanMoment.PICK);
    }

    private double produce() {
        return ConstructionUses.bestProduction(inputs()).orElseThrow().value();
    }

    private double fullBuild() {
        return CardValueScale.PRODUCTION_SHARE
                * ProductionPlanner.plan(test.game, test.nekro, home).value();
    }

    @Test
    void isWorthTheSavedTokenWhenATacticalActionCouldBuildThere() {
        double token = TokenValue.savedTacticToken(inputs());

        assertThat(fullBuild()).isGreaterThan(token);
        assertThat(produce()).isGreaterThanOrEqualTo(token).isLessThan(fullBuild());
    }

    @Test
    void isWorthTheWholeBuildOnceTheDockSystemIsActivated() {
        home.addCC(Mapper.getCCID(test.nekro.getColor()));
        changed();

        assertThat(produce()).isCloseTo(fullBuild(), within(1e-9));
    }

    @Test
    void isWorthTheWholeBuildWithoutATacticToken() {
        test.nekro.setTacticalCC(0);
        changed();

        assertThat(produce()).isCloseTo(fullBuild(), within(1e-9));
    }
}
