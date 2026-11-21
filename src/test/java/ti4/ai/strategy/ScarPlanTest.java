package ti4.ai.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

// A faction technology from an Entropic Scar is bought at the status phase for a strategy token. A plan that counts on
// one has a step still to take: it is not ownership, and it must not read as ready to score.
class ScarPlanTest extends BaseTi4Test {

    private AiTestGame test;
    private Tile scar;

    @BeforeEach
    void setUp() {
        ResearchPlan.forgetAll();
        test = new AiTestGame();
        test.nekroHome();
        String position = AiTestGame.neighbourOf(AiTestGame.HOME);
        test.solHomeBeside(position);
        scar = test.place("114", position);
        test.sol.setSecret("ans");
        test.sol.setTacticalCC(3);
        test.sol.setStrategicCC(1);
        test.units(scar, "space", test.sol, UnitType.Destroyer, 1);
    }

    @AfterEach
    void tearDown() {
        ResearchPlan.forgetAll();
    }

    @Test
    void aScarPurchaseIsARouteStepNotOwnership() {
        test.sol.addTech("ac2");

        TechPath.Route route = TechPath.route(test.game, test.sol, "ans").orElseThrow();

        assertThat(route.researches()).isZero();
        assertThat(route.scarTechs()).containsExactly("so2");
        assertThat(route.needsScarPurchase()).isTrue();
    }

    @Test
    void thePlanDoesNotCallAScarPurchaseReadyToScore() {
        test.sol.addTech("ac2");

        ResearchPlan plan = ResearchPlan.of(test.game, test.sol);

        assertThat(plan.goals()).anySatisfy(goal -> {
            assertThat(goal.id()).isEqualTo("ans");
            assertThat(goal.status()).isEqualTo(ResearchPlan.Status.SCAR_PURCHASE);
        });
        assertThat(plan.describe()).doesNotContain("ready to score").contains("Entropic Scar purchase");
    }

    @Test
    void ownedTechnologiesAreReadyToScore() {
        test.sol.addTech("ac2");
        test.sol.addTech("so2");

        ResearchPlan plan = ResearchPlan.of(test.game, test.sol);

        assertThat(plan.goals()).anySatisfy(goal -> {
            assertThat(goal.id()).isEqualTo("ans");
            assertThat(goal.status()).isEqualTo(ResearchPlan.Status.READY);
        });
        assertThat(TechPath.route(test.game, test.sol, "ans").orElseThrow().needsScarPurchase())
                .isFalse();
    }

    @Test
    void withoutAStrategyTokenTheScarSuppliesNothing() {
        test.sol.addTech("ac2");
        test.sol.setStrategicCC(0);

        TechPath.Route route = TechPath.route(test.game, test.sol, "ans").orElseThrow();

        assertThat(route.scarTechs()).isEmpty();
        assertThat(route.researches()).isPositive();
        assertThat(ResearchPlan.of(test.game, test.sol).goals())
                .noneMatch(goal -> goal.status() == ResearchPlan.Status.READY
                        || goal.status() == ResearchPlan.Status.SCAR_PURCHASE);
    }

    @Test
    void thePlanRefreshesWhenTheTokenIsSpent() {
        test.sol.addTech("ac2");
        assertThat(ResearchPlan.of(test.game, test.sol).goals())
                .anyMatch(goal -> goal.status() == ResearchPlan.Status.SCAR_PURCHASE);

        test.sol.setStrategicCC(0);

        assertThat(ResearchPlan.of(test.game, test.sol).goals())
                .noneMatch(goal -> goal.status() == ResearchPlan.Status.SCAR_PURCHASE);
    }

    @Test
    void aPurchaseThatCompletesTheSecretIsRecognisedWhateverTheGameHorizon() {
        test.sol.addTech("ac2");

        assertThat(ScarToken.completesAdaptNewStrategies(test.game, test.sol, "so2"))
                .isTrue();
        assertThat(ScarToken.completesAdaptNewStrategies(test.game, test.sol, "md"))
                .isFalse();
    }

    @Test
    void aPurchaseThatLeavesTheSecretShortIsNotACompletion() {
        assertThat(ScarToken.completesAdaptNewStrategies(test.game, test.sol, "so2"))
                .isFalse();
    }

    @Test
    void nothingToCompleteWithoutTheSecret() {
        test.sol.addTech("ac2");
        test.sol.removeSecret(test.sol.getSecretsUnscored().get("ans"));

        assertThat(ScarToken.completesAdaptNewStrategies(test.game, test.sol, "so2"))
                .isFalse();
    }

    @Test
    void canBuyNeedsATokenToPayWith() {
        assertThat(ScarToken.canBuy(test.game, test.sol)).isTrue();

        test.sol.setStrategicCC(0);

        assertThat(ScarToken.canBuy(test.game, test.sol)).isFalse();
    }
}
