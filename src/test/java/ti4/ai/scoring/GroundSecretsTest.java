package ti4.ai.scoring;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.tactical.TacticalPlan;
import ti4.ai.tactical.TacticalPlan.Kind;
import ti4.ai.tactical.TacticalPlanner;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

// Mechanize the Military (a mech on each of 4 planets) and Occupy the Fringe (9 ground forces on a planet without a
// space dock) are valued like the other board secrets, so the planner can pursue them.
class GroundSecretsTest extends BaseTi4Test {

    private static final String MECHANIZE = "mtm";
    private static final String OCCUPY = "otf";

    private AiTestGame test;
    private Tile home;
    private String neighbour;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.game.setPhaseOfGame("action");
        home = test.nekroHome();
        neighbour = AiTestGame.neighbourOf(AiTestGame.HOME);
    }

    // Mechs on Mordai II, Abyz and Fria: carrying a fourth to Lirta IV completes the secret.
    @Test
    void aMechLandedOnAFourthPlanetCompletesMechanizeTheMilitary() {
        test.nekro.setSecret(MECHANIZE);
        Tile abyzFria = test.place("38", neighbour);
        test.place("35", "203");
        test.nekro.addPlanet("abyz");
        test.nekro.addPlanet("fria");
        test.units(home, "mordaiii", test.nekro, UnitType.Mech, 1);
        test.units(abyzFria, "abyz", test.nekro, UnitType.Mech, 1);
        test.units(abyzFria, "fria", test.nekro, UnitType.Mech, 1);
        ObjectiveValue value = new ObjectiveValue(test.game, test.nekro);

        Footprint landed = value.before()
                .after("203", List.of(new Footprint.Move(AiTestGame.HOME, UnitType.Mech, 1)), List.of("lirtaiv"));
        Footprint infantryOnly = value.before()
                .after("203", List.of(new Footprint.Move(AiTestGame.HOME, UnitType.Infantry, 1)), List.of("lirtaiv"));

        assertThat(value.progress(MECHANIZE, value.before())).isEqualTo(3);
        assertThat(value.completedPoints(landed)).isEqualTo(1);
        assertThat(value.completedPoints(infantryOnly)).isZero();
    }

    // Tarmann holds 5 infantry; 4 more landed there make 9 on a planet without a dock. Mordai II has a dock, so its
    // ground forces never count.
    @Test
    void groundForcesStackedOnAPlanetWithoutADockCompleteOccupyTheFringe() {
        test.nekro.setSecret(OCCUPY);
        Tile tarmann = test.place("22", neighbour);
        test.nekro.addPlanet("tarmann");
        test.units(tarmann, "tarmann", test.nekro, UnitType.Infantry, 5);
        test.units(home, "mordaiii", test.nekro, UnitType.Infantry, 9);
        ObjectiveValue value = new ObjectiveValue(test.game, test.nekro);

        Footprint after = value.before()
                .after(
                        neighbour,
                        List.of(new Footprint.Move(AiTestGame.HOME, "mordaiii", UnitType.Infantry, 4)),
                        List.of("tarmann"));

        assertThat(value.progress(OCCUPY, value.before())).isEqualTo(5);
        assertThat(value.completedPoints(after)).isEqualTo(1);
    }

    // With Occupy the Fringe in hand, a carrier at home loads its infantry and lands them on Tarmann.
    @Test
    void thePlannerGarrisonsAPlanetWithoutADockForOccupyTheFringe() {
        test.nekro.setSecret(OCCUPY);
        test.nekro.setTacticalCC(3);
        Tile tarmann = test.place("22", neighbour);
        test.nekro.addPlanet("tarmann");
        test.units(tarmann, "tarmann", test.nekro, UnitType.Infantry, 5);
        test.units(home, "space", test.nekro, UnitType.Carrier, 1);
        test.units(home, "space", test.nekro, UnitType.Infantry, 4);

        TacticalPlan plan =
                TacticalPlanner.forTarget(test.game, test.nekro, neighbour).orElseThrow();

        assertThat(plan.kind()).isEqualTo(Kind.GARRISON);
        assertThat(plan.landings()).containsEntry("tarmann", 9);
    }

    // With Mechanize the Military in hand and mechs on Mordai II and Abyz, a carrier at home takes a spare mech to
    // Tarmann, a held planet without one.
    @Test
    void thePlannerSpreadsMechsForMechanizeTheMilitary() {
        test.nekro.setSecret(MECHANIZE);
        test.nekro.setTacticalCC(3);
        Tile tarmann = test.place("22", neighbour);
        test.nekro.addPlanet("tarmann");
        test.units(tarmann, "tarmann", test.nekro, UnitType.Infantry, 1);
        test.units(home, "mordaiii", test.nekro, UnitType.Mech, 2);
        test.units(home, "space", test.nekro, UnitType.Carrier, 1);

        TacticalPlan plan =
                TacticalPlanner.forTarget(test.game, test.nekro, neighbour).orElseThrow();

        assertThat(plan.kind()).isEqualTo(Kind.GARRISON);
        assertThat(plan.landings()).containsEntry("tarmann", 2);
        assertThat(plan.moves()).allMatch(move -> move.type() != UnitType.Infantry);
    }

    // A carrier one system away is worth more coming home for spare mechs while Mechanize the Military is in hand. The
    // home planets are exhausted, so building there is not on offer.
    @Test
    void aCarrierComesHomeForSpareMechsWhileMechanizeIsHeld() {
        test.nekro.setTacticalCC(3);
        Tile tarmann = test.place("22", neighbour);
        test.nekro.addPlanet("tarmann");
        test.units(home, "mordaiii", test.nekro, UnitType.Mech, 3);
        test.units(tarmann, "space", test.nekro, UnitType.Carrier, 1);
        new java.util.ArrayList<>(test.nekro.getPlanets()).forEach(test.nekro::exhaustPlanet);
        test.nekro.setTg(0);
        double without = TacticalPlanner.forTarget(test.game, test.nekro, AiTestGame.HOME)
                .filter(plan -> plan.kind() == Kind.PICKUP)
                .map(TacticalPlan::score)
                .orElse(0.0);

        test.nekro.setSecret(MECHANIZE);
        TacticalPlan plan = TacticalPlanner.forTarget(test.game, test.nekro, AiTestGame.HOME)
                .orElseThrow();

        assertThat(plan.kind()).isEqualTo(Kind.PICKUP);
        assertThat(plan.score()).isGreaterThan(without + 1);
    }

    // Expanding to Qucen'n and Rarron (neither hazardous) from home with two mechs and four infantry: the carrier
    // holds four, and while Mechanize the Military is held it takes the mechs, one for each planet.
    @Test
    void anExpansionCarriesMechsWhileMechanizeIsHeld() {
        test.nekro.setTacticalCC(3);
        test.place("29", neighbour);
        test.units(home, "mordaiii", test.nekro, UnitType.Mech, 2);
        test.units(home, "mordaiii", test.nekro, UnitType.Infantry, 4);
        test.units(home, "space", test.nekro, UnitType.Carrier, 1);
        TacticalPlan without =
                TacticalPlanner.forTarget(test.game, test.nekro, neighbour).orElseThrow();

        test.nekro.setSecret(MECHANIZE);
        TacticalPlan plan =
                TacticalPlanner.forTarget(test.game, test.nekro, neighbour).orElseThrow();

        assertThat(without.moves()).noneMatch(move -> move.type() == UnitType.Mech);
        assertThat(plan.kind()).isEqualTo(Kind.EXPAND);
        assertThat(plan.moves())
                .filteredOn(move -> move.type() == UnitType.Mech)
                .extracting(TacticalPlan.UnitMove::count)
                .containsExactly(2);
    }

    // A forward dock on Qucen'n with two mechs and a carrier beside it: building there first lifts a spare mech onto
    // Rarron, the other planet of the system, then produces.
    @Test
    void aForwardDockShiftsAMechToItsOtherPlanetBeforeBuilding() {
        test.nekro.setSecret(MECHANIZE);
        test.nekro.setTacticalCC(3);
        test.nekro.setTg(6);
        Tile dock = test.place("29", neighbour);
        test.nekro.addPlanet("qucenn");
        test.nekro.addPlanet("rarron");
        test.units(dock, "qucenn", test.nekro, UnitType.Spacedock, 1);
        test.units(dock, "qucenn", test.nekro, UnitType.Mech, 2);
        test.units(dock, "space", test.nekro, UnitType.Carrier, 1);

        TacticalPlan plan =
                TacticalPlanner.forTarget(test.game, test.nekro, neighbour).orElseThrow();

        assertThat(plan.kind()).isEqualTo(Kind.PRODUCE);
        assertThat(plan.moves()).containsExactly(new TacticalPlan.UnitMove(neighbour, "qucenn", UnitType.Mech, 1));
        assertThat(plan.landings()).containsEntry("rarron", 1);
    }

    // No ship with capacity sits at the dock, so the carrier at home comes along to do the lifting.
    @Test
    void aCarrierFromNextDoorDoesTheLiftingWhenNoneIsAtTheDock() {
        test.nekro.setSecret(MECHANIZE);
        test.nekro.setTacticalCC(3);
        test.nekro.setTg(6);
        Tile dock = test.place("29", neighbour);
        test.nekro.addPlanet("qucenn");
        test.nekro.addPlanet("rarron");
        test.units(dock, "qucenn", test.nekro, UnitType.Spacedock, 1);
        test.units(dock, "qucenn", test.nekro, UnitType.Mech, 2);
        test.units(home, "space", test.nekro, UnitType.Carrier, 1);

        TacticalPlan plan =
                TacticalPlanner.forTarget(test.game, test.nekro, neighbour).orElseThrow();

        assertThat(plan.kind()).isEqualTo(Kind.PRODUCE);
        assertThat(plan.moves())
                .contains(
                        new TacticalPlan.UnitMove(AiTestGame.HOME, "space", UnitType.Carrier, 1),
                        new TacticalPlan.UnitMove(neighbour, "qucenn", UnitType.Mech, 1));
        assertThat(plan.landings()).containsEntry("rarron", 1);
    }
}
