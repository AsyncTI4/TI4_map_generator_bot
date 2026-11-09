package ti4.ai.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.EnumSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiExperiments;
import ti4.ai.AiTestGame;
import ti4.ai.eval.ValueScale;
import ti4.game.Player;
import ti4.testUtils.BaseTi4Test;

// The Technology card's research benefit, on the trade good scale of the other cards: the free research is worth
// TECH_TEMPO times its research value, and the second research is the second-best tech at TECH_TEMPO minus the
// 6 resources it costs.
class TechnologyCardTradeGoodValueTest extends BaseTi4Test {

    private static final int TECHNOLOGY = 7;
    private static final double TOLERANCE = 1e-9;

    private AiTestGame test;
    private Player sol;
    private long modified = 1;

    @BeforeEach
    void setUp() {
        test = AiTestGame.withSolAi();
        sol = test.sol;
        test.game.setRound(2);
        test.solHomeBeside("305");
        test.isActive(sol, "action");
        changed();
    }

    private void changed() {
        test.game.setLastModifiedDate(modified++);
    }

    private double research() {
        return StrategyCardPlanner.plan(PlanInputs.of(test.game, sol, PlanMoment.ACTION), TECHNOLOGY)
                .benefit(CardUse.TECHNOLOGY_RESEARCH)
                .orElseThrow()
                .value();
    }

    private double firstResearchValue() {
        String first = ResearchPolicy.bestResearchable(test.game, sol).orElseThrow();
        return ResearchPolicy.value(test.game, sol, first);
    }

    private double secondResearchValue() {
        String first = ResearchPolicy.bestResearchable(test.game, sol).orElseThrow();
        String second =
                ResearchPolicy.bestResearchableExcept(test.game, sol, first).orElseThrow();
        assertThat(second).isNotEqualTo(first);
        return ResearchPolicy.value(test.game, sol, second);
    }

    private void disableExperiment() {
        AiExperiments.disable(sol, EnumSet.of(AiExperiments.Experiment.SPEAKER_AND_TECH));
        changed();
    }

    // No trade goods beyond Jord: the second research is not affordable, so only the free one counts, scaled by
    // TECH_TEMPO.
    @Test
    void scalesTheFreeResearchByTechTempo() {
        sol.setTg(0);
        changed();

        assertThat(research()).isCloseTo(ResearchPolicy.TECH_TEMPO * firstResearchValue(), within(TOLERANCE));
    }

    // Plenty of trade goods: the second research adds the second-best tech at TECH_TEMPO, minus 6 resources.
    @Test
    void valuesTheSecondResearchAsTheSecondBestTechMinusItsCost() {
        sol.setTg(20);
        changed();
        double expectedSecond = Math.max(
                0,
                ResearchPolicy.TECH_TEMPO * secondResearchValue()
                        - StrategyCardRules.SECOND_TECH_COST * ValueScale.RESOURCE);

        assertThat(research())
                .isCloseTo(ResearchPolicy.TECH_TEMPO * firstResearchValue() + expectedSecond, within(TOLERANCE));
    }

    // The control seat keeps the old raw research units: the best tech, plus half of it again for the second.
    @Test
    void controlSeatKeepsTheRawResearchUnits() {
        sol.setTg(20);
        disableExperiment();

        assertThat(research())
                .isCloseTo(firstResearchValue() * (1 + CardValueScale.SECOND_RESEARCH_SHARE), within(TOLERANCE));
    }
}
