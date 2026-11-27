package ti4.ai.tactical;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiExperiments;
import ti4.ai.AiExperiments.Experiment;
import ti4.ai.eval.BoardView;
import ti4.ai.eval.FractureCampaign;
import ti4.ai.nekro.CommandTokenPolicy;
import ti4.ai.nekro.CommandTokenPolicy.Pool;
import ti4.ai.tactical.TacticalPlan.Kind;
import ti4.ai.tactical.TacticalPlan.UnitMove;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

// STYX_TAKE gates the Styx parts of 92db32a718: gathering credit, the long march horizon, the rival-sized fleet, the
// fleet tokens kept for gathering ships and the later-landing credit of a space clear. A seat with the experiment off
// plans Styx like any other Fracture prize again (three-step staging, the fixed fleet of three dreadnoughts and a
// carrier). The other Fracture prizes keep the new planning either way. The flag is a per-seat setting fixed before
// play, so each test switches it off before the first planning call.
class StyxTakeExperimentTest extends BaseTi4Test {

    private FractureScenario fracture;

    @BeforeEach
    void setUp() {
        fracture = new FractureScenario();
    }

    private Game game() {
        return fracture.board.test.game;
    }

    private Tile styx() {
        return fracture.fracture(FractureScenario.STYX);
    }

    private void takeOff(Player seat) {
        AiExperiments.disable(seat, Set.of(Experiment.STYX_TAKE));
    }

    // Two dreadnoughts and a carrier win Styx's space fight about two times in five: below the landing gate. With the
    // take on that still earns part of the prize; with it off a strike below the gate is worth nothing, as before.
    private double partWayFuture(boolean takeOn) {
        if (!takeOn) takeOff(fracture.nekro());
        Tile beside = fracture.fracture("frac3");
        fracture.ships(beside, UnitType.Dreadnought, 2);
        fracture.ships(beside, UnitType.Carrier, 1);
        TacticalPlanner.Context context = new TacticalPlanner.Context(game(), fracture.nekro());
        List<UnitMove> cargo = List.of(new UnitMove(beside.getPosition(), BoardView.SPACE, UnitType.Infantry, 4));
        return TacticalPlanner.futureScore(context, styx(), game().getNeutral(), beside, List.of(), cargo);
    }

    @Test
    void gatheringTowardNeutralStyxEarnsNothingWithTheTakeOff() {
        assertThat(partWayFuture(true)).isPositive();

        fracture = new FractureScenario();
        assertThat(partWayFuture(false)).isZero();
    }

    // The same part-way gathering toward Cocytus, a Fracture prize that is not Styx, is valued the same with the take
    // on and off: the flag reverts only Styx.
    private double partWayCocytusFuture(boolean takeOn) {
        if (!takeOn) takeOff(fracture.nekro());
        Tile beside = fracture.fracture("frac2");
        fracture.ships(beside, UnitType.Dreadnought, 2);
        fracture.ships(beside, UnitType.Carrier, 1);
        TacticalPlanner.Context context = new TacticalPlanner.Context(game(), fracture.nekro());
        List<UnitMove> cargo = List.of(new UnitMove(beside.getPosition(), BoardView.SPACE, UnitType.Infantry, 4));
        return TacticalPlanner.futureScore(
                context, fracture.fracture(FractureScenario.COCYTUS), game().getNeutral(), beside, List.of(), cargo);
    }

    @Test
    void aPrizeOtherThanStyxIsValuedTheSameWithTheTakeOff() {
        double on = partWayCocytusFuture(true);
        assertThat(on).isPositive();

        fracture = new FractureScenario();
        double off = partWayCocytusFuture(false);
        assertThat(off).isEqualTo(on);
    }

    @Test
    void styxStagesThreeStepsOutWithTheTakeOff() {
        Player neutral = game().getNeutral();
        assertThat(TacticalPlanner.stageLead(new TacticalPlanner.Context(game(), fracture.nekro()), neutral, styx()))
                .isEqualTo(10);

        fracture = new FractureScenario();
        takeOff(fracture.nekro());
        assertThat(TacticalPlanner.stageLead(
                        new TacticalPlanner.Context(game(), fracture.nekro()), game().getNeutral(), styx()))
                .isEqualTo(TacticalPlanner.stageLead(game().getNeutral()));
    }

    // Mirrors FractureTargetsTest.aStrongerRivalFleetMakesTheDockBuildMoreDreadnoughtsForStyx: with the take off a
    // seven-dreadnought rival no longer grows the Styx fleet, so the dock builds the same dreadnoughts either way.
    @Test
    void theDockBuildsTheFixedStyxFleetWithTheTakeOff() {
        takeOff(fracture.nekro());
        fracture.ships(fracture.board.home, UnitType.Dreadnought, 3);
        fracture.ships(fracture.board.home, UnitType.Carrier, 1);
        fracture.board.test.units(fracture.board.home, "mordaiii", fracture.nekro(), UnitType.Infantry, 6);
        fracture.nekro().setFleetCC(8);
        fracture.nekro().setTg(20);
        int alone = ProductionPlanner.plan(game(), fracture.nekro(), fracture.board.home)
                .units(UnitType.Dreadnought);

        rivalStack(7);
        int facingTheRival = ProductionPlanner.plan(game(), fracture.nekro(), fracture.board.home)
                .units(UnitType.Dreadnought);

        assertThat(FractureCampaign.fleetShips(game(), fracture.nekro())).isEqualTo(FractureCampaign.TAKING_SHIPS);
        assertThat(FractureCampaign.infantry(game(), fracture.nekro())).isEqualTo(FractureCampaign.INFANTRY);
        assertThat(facingTheRival).isEqualTo(alone);
    }

    // The same rival with the take on raises both, so the assertions above are the flag and not the setup.
    @Test
    void aRivalStillGrowsTheStyxFleetWithTheTakeOn() {
        rivalStack(7);

        assertThat(FractureCampaign.fleetShips(game(), fracture.nekro())).isEqualTo(7);
        assertThat(FractureCampaign.infantry(game(), fracture.nekro())).isGreaterThan(FractureCampaign.INFANTRY);
    }

    // Three dreadnoughts and a carrier beside the ingress reach Styx in two actions (StyxGatheringFleetTest). With the
    // take off they ask for no gathering fleet; the flag is per seat, so another seat's switch changes nothing here.
    @Test
    void noGatheringFleetWithTheTakeOff() {
        fracture.ships("frac2", UnitType.Dreadnought, 3);
        fracture.ships("frac2", UnitType.Carrier, 1);
        takeOff(fracture.board.sardakk);
        assertThat(FractureCampaign.gatheringFleet(game(), fracture.nekro())).isEqualTo(4);

        takeOff(fracture.nekro());
        assertThat(FractureCampaign.gatheringFleet(game(), fracture.nekro())).isZero();
    }

    // FractureTargetsTest.sevenTokensAreEnoughForTheFleetThatTakesStyx keeps four fleet tokens for the Styx fleet out
    // of seven. With the take off the early Styx-fleet level is gone, so seven tokens no longer reach four fleet.
    @Test
    void noEarlyStyxFleetTokensWithTheTakeOff() {
        game().setPhaseOfGame("statusHomework");
        Map<Pool, Integer> on = CommandTokenPolicy.ideal(game(), fracture.nekro(), 7);

        takeOff(fracture.nekro());
        Map<Pool, Integer> off = CommandTokenPolicy.ideal(game(), fracture.nekro(), 7);

        assertThat(on.get(Pool.FLEET)).isEqualTo(FractureCampaign.TAKING_SHIPS);
        assertThat(off.get(Pool.FLEET)).isLessThan(FractureCampaign.TAKING_SHIPS);
    }

    // Four dreadnoughts on frac3 clear Styx's space; a carrier on frac5 could bring ground forces next round. With the
    // take on that clear earns a later landing (StyxTransportStrikeTest); with it off it earns none.
    @Test
    void aSpaceClearEarnsNoLaterLandingWithTheTakeOff() {
        assertThat(clearLaterLanding()).isPositive();

        fracture = new FractureScenario();
        takeOff(fracture.nekro());
        assertThat(clearLaterLanding()).isZero();
    }

    private double clearLaterLanding() {
        fracture.ships("frac3", UnitType.Dreadnought, 4);
        fracture.ships("frac5", UnitType.Carrier, 1);
        TacticalPlanner.Context context = new TacticalPlanner.Context(game(), fracture.nekro());
        return TacticalPlanner.candidatePool(context, styx(), game().getNeutral()).stream()
                .filter(found -> found.estimate().landings().isEmpty() && moves(found, "frac3", UnitType.Dreadnought))
                .filter(found -> !moves(found, "frac5", UnitType.Carrier))
                .findFirst()
                .orElseThrow()
                .terms()
                .laterLanding();
    }

    // A dreadnought, a carrier and four infantry at home are far short of Styx's guard. With the take on they still
    // march for less than the taking fleet (FractureTargetsTest); with it off they fall below the staging bar.
    @Test
    void aFleetTooSmallForStyxDoesNotMarchWithTheTakeOff() {
        smallFleetAtHome();
        assertThat(stageToIngress()).isPresent();

        fracture = new FractureScenario();
        takeOff(fracture.nekro());
        smallFleetAtHome();
        assertThat(stageToIngress()).isEmpty();
    }

    private void smallFleetAtHome() {
        fracture.ships(fracture.board.home, UnitType.Dreadnought, 1);
        fracture.ships(fracture.board.home, UnitType.Carrier, 1);
        fracture.ships(fracture.board.home, UnitType.Infantry, 4);
    }

    private Optional<TacticalPlan> stageToIngress() {
        return TacticalPlanner.ranked(game(), fracture.nekro()).stream()
                .filter(plan -> plan.kind() == Kind.STAGE && plan.target().equals(fracture.ingress.getPosition()))
                .findFirst();
    }

    private static boolean moves(TacticalPlanner.Candidate candidate, String origin, UnitType type) {
        return candidate.moves().stream()
                .anyMatch(move -> move.origin().equals(origin) && move.type() == type && move.count() > 0);
    }

    private void rivalStack(int dreadnoughts) {
        Player sardakk = fracture.board.sardakk;
        sardakk.setFleetCC(dreadnoughts);
        Tile far = fracture.board.test.place("27", "101");
        fracture.board.test.units(far, "space", sardakk, UnitType.Dreadnought, dreadnoughts);
    }
}
