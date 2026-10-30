package ti4.ai.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import org.assertj.core.data.Offset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.scoring.ScoringStakes;
import ti4.game.Tile;
import ti4.helpers.Constants;
import ti4.model.Mapper;
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

    // Destroyer II rolls three anti-fighter barrage dice at 6 instead of two at 9, which Fight with Precision needs to
    // clear a stack of fighters. It adds 0.3 of a point while the secret is held and the two red technologies it
    // requires are owned; the AI does not research toward it.
    @Test
    void valuesDestroyerIiForFightWithPrecisionOnlyWhenItCanBeResearchedNow() {
        double plain = ResearchPolicy.value(test.game, test.nekro, "dd2");
        test.nekro.setSecret("fwp");
        assertThat(ResearchPolicy.value(test.game, test.nekro, "dd2")).isEqualTo(plain);
        test.nekro.removeSecret(test.nekro.getSecretsUnscored().get("fwp"));

        test.nekro.addTech("ps");
        test.nekro.addTech("da");
        double without = ResearchPolicy.value(test.game, test.nekro, "dd2");
        test.nekro.setSecret("fwp");

        assertThat(ResearchPolicy.value(test.game, test.nekro, "dd2") - without)
                .isCloseTo(0.3 * ScoringStakes.scoreValue(test.game, test.nekro, 1), Offset.offset(1e-9));
    }

    // Two docks on Qucenn (1 resource) and Rarron (0) make 3 + 2 = 5 production. Space Dock II lifts every dock by 2,
    // to
    // 9, which completes Produce en Masse; a flat 2 would only reach 7.
    @Test
    void valuesSpaceDockIiForEachDockInTheBestSystemWhileProduceEnMassIsHeld() {
        ti4.game.Tile tile = test.place("29", "101");
        test.nekro.addPlanet("qucenn");
        test.nekro.addPlanet("rarron");
        test.units(tile, "qucenn", test.nekro, ti4.helpers.Units.UnitType.Spacedock, 1);
        test.units(tile, "rarron", test.nekro, ti4.helpers.Units.UnitType.Spacedock, 1);
        double without = ResearchPolicy.value(test.game, test.nekro, "sd2");
        test.nekro.setSecret("pem");

        assertThat(ResearchPolicy.value(test.game, test.nekro, "sd2")).isGreaterThan(without);
    }

    private void frontierTokenNextToHome() {
        test.nekroHome();
        Tile neighbour = test.place("28", AiTestGame.neighbourOf(AiTestGame.HOME));
        neighbour.addToken(Mapper.getTokenID(Constants.FRONTIER), "space");
    }

    // Dark Energy Tap explores the frontier tokens a seat's ships can reach, so it is worth something only when
    // there are some nearby.
    @Test
    void valuesDarkEnergyTapByTheFrontierTokensNearby() {
        test.nekroHome();
        assertThat(ResearchPolicy.frontierTapValue(test.game, test.nekro, "det"))
                .isZero();

        frontierTokenNextToHome();

        assertThat(ResearchPolicy.frontierTapValue(test.game, test.nekro, "det"))
                .isPositive();
        assertThat(ResearchPolicy.frontierTapValue(test.game, test.nekro, "amd"))
                .isZero();
    }

    // Late in the game there are fewer rounds in which to explore.
    @Test
    void valuesDarkEnergyTapLessWhenTheGameIsAboutToEnd() {
        frontierTokenNextToHome();
        double early = ResearchPolicy.frontierTapValue(test.game, test.nekro, "det");

        test.points(test.nekro, 9);

        assertThat(ResearchPolicy.frontierTapValue(test.game, test.nekro, "det"))
                .isLessThan(early);
    }
}
