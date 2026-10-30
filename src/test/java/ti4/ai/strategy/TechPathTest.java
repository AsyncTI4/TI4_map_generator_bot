package ti4.ai.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.OptionalInt;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.secrets.SecretValue;
import ti4.model.Mapper;
import ti4.testUtils.BaseTi4Test;

// Sol starts with Antimass Deflectors (blue) and Neural Motivator (green). Its faction technologies are Advanced
// Carrier II (BB) and Spec Ops II (GG), both unit upgrades, so they add no colour of their own.
class TechPathTest extends BaseTi4Test {

    private AiTestGame test;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.sol.setSecret("ans");
    }

    // Each faction technology is one research plus one research for every prerequisite Sol lacks: Advanced Carrier II
    // needs a second blue technology and Spec Ops II a second green one, so two faction technologies cost 2 + 2.
    @Test
    void countsThePrerequisitesAndTheFactionTechnologiesThemselves() {
        assertThat(needed("ans")).hasValue(4);

        test.sol.addTech("gd");

        assertThat(needed("ans")).hasValue(3);
    }

    // A specialty planet covers one missing prerequisite, so Tarmann (biotic) saves the second green technology.
    @Test
    void aSpecialtyPlanetStandsInForAPrerequisite() {
        test.place("22", "101");
        test.sol.addPlanet("tarmann");

        assertThat(needed("ans")).hasValue(3);
    }

    // With the Sol breakthrough (biotic and cybernetic synergy) a cybernetic technology counts as green, so Neural
    // Motivator and Scanlink Drone Network meet Spec Ops II's two green prerequisites. Without it they do not.
    @Test
    void aBreakthroughSynergyCoversAPrerequisite() {
        test.sol.addTech("sdn");
        assertThat(needed("ans")).hasValue(4);

        test.sol.addBreakthrough("solbt");
        test.sol.setBreakthroughUnlocked("solbt", true);

        assertThat(needed("ans")).hasValue(3);
        assertThat(PrerequisiteSkips.missing(test.sol, Mapper.getTech("so2"))).isEmpty();
    }

    // Only the researches left can be spent: with the leader on 8 of 10 points one research remains and four are
    // needed, so the path is not walked and no technology is pulled towards it.
    @Test
    void doesNotChaseAPathTheGameWillNotLast() {
        assertThat(TechPath.reachable(test.game, test.sol, "ans")).isTrue();
        assertThat(TechPath.gain(test.game, test.sol, "ans", Mapper.getTech("dxa")))
                .isPositive();

        test.points(test.nekro, 8);

        assertThat(TechPath.researchesLeft(test.game)).isEqualTo(1);
        assertThat(TechPath.reachable(test.game, test.sol, "ans")).isFalse();
        assertThat(TechPath.gain(test.game, test.sol, "ans", Mapper.getTech("dxa")))
                .isZero();
    }

    // Nekro's only faction technologies are Valefar Assimilators, which never count, and it cannot research.
    @Test
    void nekroCanNeverScoreAdaptNewStrategies() {
        assertThat(TechPath.researchesNeeded(test.game, test.nekro, "ans", null))
                .isEmpty();
        assertThat(TechPath.reachable(test.game, test.nekro, "ans")).isFalse();
    }

    // A technology on the route carries its share of the point: a second green technology saves one of the four
    // researches. One off the route (red) is worth no more than it was before.
    @Test
    void liftsTheTechnologiesOnTheRoute() {
        double onRoute = TechPath.gain(test.game, test.sol, "ans", Mapper.getTech("dxa"));
        double offRoute = TechPath.gain(test.game, test.sol, "ans", Mapper.getTech("md"));

        assertThat(onRoute).isCloseTo(TechPath.STEP_SHARE / 4, Offset.offset(1e-9));
        assertThat(offRoute).isZero();
    }

    // The faction technology that finishes the route is worth the whole point.
    @Test
    void theLastFactionTechnologyIsWorthAWholePoint() {
        test.sol.addTech("gd");
        test.sol.addTech("dxa");
        test.sol.addTech("ac2");

        assertThat(needed("ans")).hasValue(1);
        assertThat(TechPath.gain(test.game, test.sol, "ans", Mapper.getTech("so2")))
                .isEqualTo(1.0);
    }

    // Holding Adapt New Strategies makes a stepping stone worth more to Sol, and researching it counts for nothing
    // when the secret is not held.
    @Test
    void holdingTheSecretLiftsTheResearchValueOfTheRoute() {
        test.sol.removeSecret(test.sol.getSecretsUnscored().get("ans"));
        double notHeld = ResearchPolicy.value(test.game, test.sol, "dxa");
        test.sol.setSecret("ans");
        double held = ResearchPolicy.value(test.game, test.sol, "dxa");

        assertThat(held - notHeld)
                .isCloseTo(ResearchPolicy.OBJECTIVE_WEIGHT * TechPath.STEP_SHARE / 4, Offset.offset(1e-9));
    }

    // Master the Laws of Physics needs four of one colour: Sol has one of each, so three more. A second green
    // technology and a second blue one leave two to go whichever colour is chosen.
    @Test
    void lawsOfPhysicsNeedsTheClosestColourToFour() {
        assertThat(needed("mlp")).hasValue(3);

        test.sol.addTech("dxa");

        assertThat(needed("mlp")).hasValue(2);
    }

    // With the Sol breakthrough, green and cybernetic technologies count together: Neural Motivator, Dacxive
    // Animators and Scanlink Drone Network are three of the four, which the bot's own progress check also counts.
    @Test
    void lawsOfPhysicsCountsSynergyTheWayTheBotDoes() {
        test.sol.addTech("dxa");
        test.sol.addTech("sdn");
        assertThat(needed("mlp")).hasValue(2);
        test.sol.addBreakthrough("solbt");
        test.sol.setBreakthroughUnlocked("solbt", true);

        assertThat(needed("mlp")).hasValue(1);
        assertThat(ti4.service.info.ListPlayerInfoService.getPlayerProgressOnObjective("mlp", test.game, test.sol))
                .isEqualTo(3);
    }

    // An objective the seat cannot reach is not worth keeping: the keep value falls to nothing once the game is
    // about to end, but not while the researches fit.
    @Test
    void keepsALongShotSecretLessOnceItCannotBeFinished() {
        double early = SecretValue.keepValue(test.game, test.sol, "ans");

        test.points(test.nekro, 8);

        assertThat(SecretValue.keepValue(test.game, test.sol, "ans")).isLessThan(early);
    }

    private OptionalInt needed(String objective) {
        return TechPath.researchesNeeded(test.game, test.sol, objective, null);
    }
}
