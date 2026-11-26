package ti4.ai.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiExperiments;
import ti4.ai.AiTestGame;
import ti4.ai.eval.DecidingSeat;
import ti4.ai.eval.FleetLimit;
import ti4.ai.xxcha.XxekirGromPlan;
import ti4.ai.xxcha.XxekirGromRules;
import ti4.game.Player;
import ti4.testUtils.BaseTi4Test;

// Per-save caches in the strategy package must not serve an answer worked out under one set of assumptions to a
// caller working under another:
// - SpendOutlook.spendingAhead is also asked while Xxekir Grom plans its own play, when the hero's readies count 0.
// - PlanInputs is worked out for a seat by whichever seat is deciding, and what it holds depends on that viewer (hand
//   knowledge, rival shortcuts, the viewer's experiments) whether or not IMPERIAL_POINTS is on.
class SpendOutlookPlanningCacheTest extends BaseTi4Test {

    private AiTestGame test;
    private long modified = 1;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
    }

    private void changed() {
        test.game.setLastModifiedDate(modified++);
    }

    // Xxcha holds its home (Archon Ren 2/3, Archon Tau 1/1), Fria (2/0) and Abyz (3/0), with Abyz and Archon Ren
    // exhausted, and Erect a Monument (8 resources) is revealed. Ready it has 3 resources; Xxekir Grom readies the two
    // exhausted planets (5 more), which makes the Monument affordable before the status phase. Counting those readies,
    // the seat keeps its 3 ready resources for the Monument and has nothing left to spend this round; without them
    // the Monument is out of reach, nothing is reserved and the seat is spending ahead.
    private Player xxchaSavingForTheMonument() {
        Player xxcha = test.addSeat(AiTestGame.SECOND_AI_ID, "xxcha", "green");
        test.isActive(xxcha, "action");
        test.place("14", "301");
        test.place("38", "201");
        for (String planet : List.of("archonren", "archontau", "fria", "abyz")) xxcha.addPlanet(planet);
        xxcha.exhaustPlanet("abyz");
        xxcha.exhaustPlanet("archonren");
        xxcha.addLeader(XxekirGromRules.HERO);
        xxcha.unsafeGetLeader(XxekirGromRules.HERO).setLocked(false);
        test.game.getRevealedPublicObjectives().put("monument", 1);
        changed();
        return xxcha;
    }

    // The contrast the planning flag hides: with the hero still locked no Grom readies are counted (as while the hero
    // plans), and the seat is spending ahead; with the hero unlocked it is not.
    @Test
    void theHerosReadiesDecideWhetherTheSeatIsSpendingAhead() {
        Player xxcha = xxchaSavingForTheMonument();
        assertThat(XxekirGromPlan.readiesThisRound(test.game, xxcha)).isPositive();
        assertThat(DiplomacyUse.spendingAhead(test.game, xxcha, true)).isFalse();

        xxcha.unsafeGetLeader(XxekirGromRules.HERO).setLocked(true);
        changed();
        assertThat(XxekirGromPlan.readiesThisRound(test.game, xxcha)).isZero();
        assertThat(DiplomacyUse.spendingAhead(test.game, xxcha, true)).isTrue();
    }

    // readiesThisRound asks SpendOutlook while the hero plans (readies counted as 0). That answer must not be served
    // to the seat's own question afterwards in the same save, which counts the readies.
    @Test
    void anAnswerWorkedOutWhileTheHeroPlansIsNotServedOutsideIt() {
        Player xxcha = xxchaSavingForTheMonument();

        int readies = XxekirGromPlan.readiesThisRound(test.game, xxcha);
        boolean outside = SpendOutlook.spendingAhead(test.game, xxcha);

        assertThat(readies).isPositive();
        assertThat(outside).isFalse();
        assertThat(outside).isEqualTo(DiplomacyUse.spendingAhead(test.game, xxcha, true));
    }

    private void nekroTable() {
        test.nekroHome();
        test.game.setVp(10);
        test.game.setSpeaker(test.nekro);
        changed();
    }

    private PlanInputs inputsSeenBy(Player viewer) {
        return DecidingSeat.of(viewer, () -> PlanInputs.of(test.game, test.nekro, PlanMoment.ACTION));
    }

    // With IMPERIAL_POINTS off for both seats the viewer used to be left out of the key, so Sol's view of the Nekro's
    // plan inputs was served to the Nekro's own decision (and the reverse).
    @Test
    void aRivalsViewIsNotServedToTheSeatsOwnDecisionWithImperialPointsOff() {
        nekroTable();
        AiExperiments.disable(test.nekro, Set.of(AiExperiments.Experiment.IMPERIAL_POINTS));
        AiExperiments.disable(test.sol, Set.of(AiExperiments.Experiment.IMPERIAL_POINTS));
        changed();

        PlanInputs rivals = inputsSeenBy(test.sol);
        PlanInputs own = inputsSeenBy(test.nekro);
        PlanInputs rivalsAgain = inputsSeenBy(test.sol);

        assertThat(own).isNotSameAs(rivals);
        assertThat(rivalsAgain).isSameAs(rivals);
    }

    // With no deciding seat the Nekro does not know its own hand (DecidingSeat.knowsHandOf); deciding for itself it
    // does. Both used to share one key, since the viewer fell back to the seat itself.
    @Test
    void inputsWorkedOutWithNoDecidingSeatAreNotServedToTheSeatsOwnDecision() {
        nekroTable();

        PlanInputs undecided = PlanInputs.of(test.game, test.nekro, PlanMoment.ACTION);
        PlanInputs own = inputsSeenBy(test.nekro);

        assertThat(own).isNotSameAs(undecided);
        assertThat(inputsSeenBy(test.nekro)).isSameAs(own);
    }

    // PlanInputs ranks the seat's tactical plans, which depend on movement "what if"s such as an unlimited fleet
    // (Darktalon Treilla asks TacticalPlanner under FleetLimit.assumingUnlimited). Inputs worked out under such an
    // assumption must not be served outside it, nor the reverse.
    @Test
    void inputsWorkedOutUnderAMovementAssumptionAreNotServedOutsideIt() {
        nekroTable();

        PlanInputs plain = PlanInputs.of(test.game, test.nekro, PlanMoment.ACTION);
        PlanInputs unlimited =
                FleetLimit.assumingUnlimited(test.nekro, () -> PlanInputs.of(test.game, test.nekro, PlanMoment.ACTION));

        assertThat(unlimited).isNotSameAs(plain);
        assertThat(PlanInputs.of(test.game, test.nekro, PlanMoment.ACTION)).isSameAs(plain);
    }
}
