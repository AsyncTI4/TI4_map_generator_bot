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
}
