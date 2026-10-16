package ti4.ai.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import org.assertj.core.data.Offset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.image.Mapper;
import ti4.testUtils.BaseTi4Test;

class ResearchPolicyTest extends BaseTi4Test {

    private AiTestGame test;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
    }

    // Sol starts with Neural Motivator, one green technology. Hyper Metabolism needs two, so any second green
    // technology is a stepping stone towards it and carries part of its value. Scanlink would be Sol's first
    // yellow technology and unlocks Predictive Intelligence, the best technology needing one yellow.
    @Test
    void valuesATechnologyThatUnlocksABetterOne() {
        double bioStims = ResearchPolicy.value(test.game, test.sol, "bs");
        double scanlink = ResearchPolicy.value(test.game, test.sol, "sdn");

        assertThat(bioStims - ResearchPolicy.baseValue(Mapper.getTech("bs")))
                .isCloseTo(0.3 * ResearchPolicy.baseValue(Mapper.getTech("hm")), Offset.offset(1e-9));
        assertThat(scanlink - ResearchPolicy.baseValue(Mapper.getTech("sdn")))
                .isCloseTo(0.3 * ResearchPolicy.baseValue(Mapper.getTech("pi")), Offset.offset(1e-9));
    }

    // Dacxive Animators is widely rated the weakest technology in the game.
    @Test
    void ranksDacxiveAnimatorsBelowEveryListedTechnology() {
        assertThat(ResearchPolicy.baseValue(Mapper.getTech("dxa")))
                .isLessThan(ResearchPolicy.baseValue(Mapper.getTech("x89")));
    }

    // Master the Sciences wants two technologies in each of four colours. Sol owns one green one, so Bio-Stims
    // still leaves six to go. Early on (about five rounds and seven researches left) that is worth chasing; once
    // someone sits at 9 of 10 points there is about one research left, so the objective adds nothing.
    @Test
    void givesUpOnATechnologyObjectiveTooFarToFinish() {
        test.game.getRevealedPublicObjectives().put("master_science", 1);
        double early = ResearchPolicy.value(test.game, test.sol, "bs");

        test.game.scorePublicObjective(test.nekro.getUserID(), test.game.addCustomPO("Test points", 9));
        double late = ResearchPolicy.value(test.game, test.sol, "bs");

        assertThat(early).isGreaterThan(late);
        assertThat(late)
                .isCloseTo(
                        ResearchPolicy.baseValue(Mapper.getTech("bs"))
                                + 0.3 * ResearchPolicy.baseValue(Mapper.getTech("hm")),
                        Offset.offset(1e-9));
    }

    // Research values are on the scale where a second Technology research (6 resources) is worth 4, and a victory
    // point is worth 8 resources, so a technology that completes a public objective gains 8 * 4 / 6 = 5.33 from it.
    // Sol with two green technologies and one blue completes Diversify Research with any second blue technology, which
    // then outranks Hyper Metabolism, the best technology that completes nothing.
    @Test
    void aTechnologyThatCompletesAnObjectiveGainsAVictoryPoint() {
        test.sol.addTech("dxa");
        test.sol.addTech("amd");
        double before = ResearchPolicy.value(test.game, test.sol, "gd");

        test.game.getRevealedPublicObjectives().put("diversify", 1);
        double after = ResearchPolicy.value(test.game, test.sol, "gd");

        assertThat(after - before).isCloseTo(8.0 * 4 / 6, Offset.offset(1e-9));
        assertThat(after).isGreaterThan(ResearchPolicy.value(test.game, test.sol, "hm"));
    }

    // About one and a half researches fit in each of the rounds the leader still needs: seven in a fresh ten point
    // game (five rounds), four with the leader on 4 (three rounds), and always at least one.
    @Test
    void researchesLeftShrinkAsTheLeaderNearsTheGoal() {
        assertThat(ResearchPolicy.researchesLeft(test.game)).isEqualTo(7);

        test.points(test.nekro, 4);
        assertThat(ResearchPolicy.researchesLeft(test.game)).isEqualTo(4);

        test.points(test.nekro, 6);
        assertThat(ResearchPolicy.researchesLeft(test.game)).isEqualTo(1);
    }
    // Hyper Metabolism and Neural Motivator pay once per status phase, so they lose value as the game shortens:
    // full value with five rounds to go, three fifths with the leader on 4 (three rounds). Gravity Drive does not.
    @Test
    void statusPhaseIncomeTechnologiesShrinkWithTheRoundsLeft() {
        double metabolism = ResearchPolicy.value(test.game, test.nekro, "hm");
        double motivator = ResearchPolicy.value(test.game, test.nekro, "nm");
        double drive = ResearchPolicy.value(test.game, test.nekro, "gd");
        assertThat(metabolism).isPositive();
        assertThat(motivator).isPositive();

        test.points(test.nekro, 4);

        assertThat(ResearchPolicy.value(test.game, test.nekro, "hm")).isCloseTo(metabolism * 0.6, Offset.offset(1e-9));
        assertThat(ResearchPolicy.value(test.game, test.nekro, "nm")).isCloseTo(motivator * 0.6, Offset.offset(1e-9));
        assertThat(ResearchPolicy.value(test.game, test.nekro, "gd")).isEqualTo(drive);
    }
}
