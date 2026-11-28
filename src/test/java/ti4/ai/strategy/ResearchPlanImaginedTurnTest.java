package ti4.ai.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.endurance.Endurance;
import ti4.ai.eval.ImaginedTurn;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.model.Mapper;
import ti4.testUtils.BaseTi4Test;

// A rival imagining Sol's turn must plan Sol's research from what the table can see: Sol's unscored secrets stay
// hidden. The research plan cache lives up to an hour, so a plan built with the secrets hidden must never be handed to
// Sol's own turn, nor the other way round.
class ResearchPlanImaginedTurnTest extends BaseTi4Test {

    private AiTestGame test;

    @BeforeEach
    void setUp() {
        ResearchPlan.forgetAll();
        test = new AiTestGame();
    }

    @AfterEach
    void tearDown() {
        ResearchPlan.forgetAll();
    }

    // Sol's own plan chases Adapt New Strategies; the imagined plan, asked right after on the same board, must not.
    @Test
    void theSeatsOwnPlanIsNotHandedToAnImaginedTurn() {
        test.sol.setSecret("ans");

        ResearchPlan own = ResearchPlan.of(test.game, test.sol);
        ResearchPlan imagined = ImaginedTurn.of(test.sol, () -> ResearchPlan.of(test.game, test.sol));

        assertThat(own.goals()).anyMatch(goal -> "ans".equals(goal.id()));
        assertThat(imagined.goals()).noneMatch(goal -> "ans".equals(goal.id()));
        assertThat(imagined.parts().get("secrets")).isEmpty();
    }

    // Fight with Precision is hidden in the imagined turn, so that plan has no barrage reach step. Built first, it
    // must not be handed to Sol's own turn, which does see the secret and wants Destroyer II for it.
    @Test
    void anImaginedPlanIsNotHandedToTheSeatsOwnTurn() {
        test.sol.setSecret("fwp");
        test.sol.addTech("ps");
        test.sol.addTech("da");

        ResearchPlan imagined = ImaginedTurn.of(test.sol, () -> ResearchPlan.of(test.game, test.sol));
        ResearchPlan own = ResearchPlan.of(test.game, test.sol);

        assertThat(imagined.steps()).noneMatch(step -> step.reasons().contains("reach: barrage secret"));
        assertThat(own.steps())
                .anyMatch(step -> "dd2".equals(step.alias()) && step.reasons().contains("reach: barrage secret"));
    }

    @Test
    void anImaginedTurnSeesOnlyThePublicObjectivesInPlay() {
        test.sol.setSecret("ans");
        test.game.getRevealedPublicObjectives().put("diversify", 1);

        assertThat(ResearchPolicy.objectivesInPlay(test.game, test.sol)).contains("ans", "diversify");
        assertThat(ImaginedTurn.of(test.sol, () -> ResearchPolicy.objectivesInPlay(test.game, test.sol)))
                .containsExactly("diversify");
    }

    // Prove Endurance makes the stalling technologies worth a little more, but only to a seat known to hold it.
    @Test
    void anImaginedTurnDoesNotValueStallTechnologyForAHiddenEnduranceSecret() {
        test.sol.setSecret(Endurance.SECRET);

        assertThat(Endurance.holds(test.sol)).isTrue();
        assertThat(Endurance.researchValue(test.sol, "sr")).isPositive();
        assertThat(ImaginedTurn.of(test.sol, () -> Endurance.holds(test.sol))).isFalse();
        assertThat(ImaginedTurn.of(test.sol, () -> Endurance.researchValue(test.sol, "sr")))
                .isZero();
    }

    // Same board as GroundCampaignTest.carrierTwoIsWorthMoreOnlyWhenItShortensTheCampaignAndCanBeResearchedNow:
    // Carrier II shortens Occupy the Fringe, so it is worth something only while the secret is visible. GroundCampaign
    // already hides the secret itself, so this guards the routed read rather than failing on the old code.
    @Test
    void anImaginedTurnDoesNotValueCarrierTwoForAHiddenGroundSecret() {
        test.game.setPhaseOfGame("action");
        Tile home = test.nekroHome();
        Tile tarmann = test.place("22", AiTestGame.neighbourOf(AiTestGame.HOME));
        test.nekro.addPlanet("tarmann");
        test.nekro.setTacticalCC(3);
        test.nekro.setTg(6);
        test.units(tarmann, "tarmann", test.nekro, UnitType.Infantry, 3);
        test.units(home, "mordaiii", test.nekro, UnitType.Infantry, 9);
        test.units(home, "space", test.nekro, UnitType.Carrier, 1);
        test.nekro.addTech("amd");
        test.nekro.addTech("gd");
        test.nekro.setSecret("otf");

        assertThat(ResearchPolicy.groundSecretValue(test.game, test.nekro, Mapper.getTech("cv2")))
                .isPositive();
        assertThat(ImaginedTurn.of(
                        test.nekro,
                        () -> ResearchPolicy.groundSecretValue(test.game, test.nekro, Mapper.getTech("cv2"))))
                .isZero();
    }
}
