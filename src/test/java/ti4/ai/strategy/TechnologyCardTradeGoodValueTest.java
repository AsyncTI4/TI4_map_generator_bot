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
//
// Sol in round 2 with Dacxive Animators on top of its starting techs: Gravity Drive is its first choice and Hyper
// Metabolism (two green) its next one, worth enough that the second research clears its 6 resource cost.
class TechnologyCardTradeGoodValueTest extends BaseTi4Test {

    private static final int TECHNOLOGY = 7;
    private static final String FIRST = "gd";
    private static final String SECOND = "hm";
    private static final double TOLERANCE = 1e-9;
    private static final double SECOND_COST = StrategyCardRules.SECOND_TECH_COST * ValueScale.RESOURCE;

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
        sol.addTech("dxa");
        changed();
    }

    private void changed() {
        test.game.setLastModifiedDate(modified++);
    }

    private double research(PlanMoment moment) {
        return StrategyCardPlanner.plan(PlanInputs.of(test.game, sol, moment), TECHNOLOGY)
                .benefit(CardUse.TECHNOLOGY_RESEARCH)
                .orElseThrow()
                .value();
    }

    private double value(String alias) {
        return ResearchPolicy.value(test.game, sol, alias);
    }

    private void disable(Player seat) {
        AiExperiments.disable(seat, EnumSet.of(AiExperiments.Experiment.SPEAKER_AND_TECH));
        changed();
    }

    @Test
    void fixtureHasTheExpectedFirstAndSecondChoices() {
        assertThat(ResearchPolicy.bestResearchable(test.game, sol)).contains(FIRST);
        assertThat(ResearchPolicy.firstResearchAsTechnologyHolder(test.game, sol))
                .contains(FIRST);
        assertThat(ResearchPolicy.bestResearchableExcept(test.game, sol, FIRST)).contains(SECOND);
    }

    // No trade goods: the second research is not affordable, so only the free one counts, scaled by TECH_TEMPO.
    @Test
    void scalesTheFreeResearchByTechTempo() {
        sol.setTg(0);
        changed();

        assertThat(research(PlanMoment.ACTION)).isCloseTo(ResearchPolicy.TECH_TEMPO * value(FIRST), within(TOLERANCE));
    }

    // Plenty of trade goods: the second research adds Hyper Metabolism at TECH_TEMPO, minus 6 resources. The term is
    // strictly positive here, so this is not the free research alone.
    @Test
    void valuesTheSecondResearchAsTheSecondBestTechMinusItsCost() {
        sol.setTg(20);
        changed();
        double second = ResearchPolicy.TECH_TEMPO * value(SECOND) - SECOND_COST;

        assertThat(second).isGreaterThan(1);
        assertThat(research(PlanMoment.ACTION))
                .isCloseTo(ResearchPolicy.TECH_TEMPO * value(FIRST) + second, within(TOLERANCE));
    }

    // At pick time the free research is the best value as Technology holder, and the second research is the best
    // tech other than the one the holder researches first.
    @Test
    void pickTimeAddsTheSecondResearchAfterTheHoldersFirstChoice() {
        sol.setTg(20);
        changed();
        double first = ResearchPolicy.TECH_TEMPO * ResearchPolicy.bestValueAsTechnologyHolder(test.game, sol);
        double second = ResearchPolicy.TECH_TEMPO * value(SECOND) - SECOND_COST;

        assertThat(TechnologyUses.secondResearchValue(PlanInputs.of(test.game, sol, PlanMoment.PICK)))
                .isCloseTo(second, within(TOLERANCE));
        assertThat(research(PlanMoment.PICK)).isCloseTo(first + second, within(TOLERANCE));
    }

    // The control seat keeps the old raw research units: the best tech, plus half of it again for the second.
    @Test
    void controlSeatKeepsTheRawResearchUnits() {
        sol.setTg(20);
        disable(sol);

        assertThat(research(PlanMoment.ACTION))
                .isCloseTo(value(FIRST) * (1 + CardValueScale.SECOND_RESEARCH_SHARE), within(TOLERANCE));
    }

    // Quantum Datahub Node and Checks and Balances value a rival's cards. A control seat looking at an experiment
    // seat's Technology card sees it on the old scale, the same value the rival would have as a control seat, so the
    // control seat's choice does not depend on the rival's experiment flag.
    @Test
    void aControlViewerSeesARivalsTechnologyCardOnTheOldScale() {
        sol.setTg(20);
        changed();
        Player viewer = test.nekro;
        disable(viewer);
        double asSeenByControl = StrategyCardRanking.baseValueAsSeenBy(test.game, viewer, sol, TECHNOLOGY);
        double asSeenByItself = StrategyCardRanking.baseValueAsSeenBy(test.game, sol, sol, TECHNOLOGY);

        disable(sol);
        double asControlSeat = StrategyCardRanking.baseValue(test.game, sol, TECHNOLOGY);

        assertThat(asSeenByControl).isCloseTo(asControlSeat, within(TOLERANCE));
        assertThat(asSeenByItself).isNotCloseTo(asSeenByControl, within(1e-3));
    }
}
