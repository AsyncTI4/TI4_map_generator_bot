package ti4.ai.tactical;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import ti4.ai.tactical.TacticalPlan.Kind;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

// Attacks on a system that holds units or planets of two other players. Each other player is weighed separately,
// starting with the one whose ships are in the system (LRR 78: the space combat is against that player alone); a
// planet of a player not worth attacking is simply not committed to (LRR 49.2), and its owner's space cannon defense
// only fires at ground forces committed to that planet (LRR 77.8a). Tile 27 holds New Albion and Starpoint. The
// seat brings two carriers, two dreadnoughts and six infantry from its home next door.
class SharedSystemAttackTest extends BaseTi4Test {

    private static final String NEW_ALBION = "newalbion";
    private static final String STARPOINT = "starpoint";
    private static final int FORTRESS = 8;

    private final DefenseScenario board = new DefenseScenario();
    private final String target = board.otherNeighbourOfHome();
    private final Tile system = board.test.place("27", target);

    private Player nekro() {
        return board.test.nekro;
    }

    private Player sardakk() {
        return board.sardakk;
    }

    private Player sol() {
        return board.test.sol;
    }

    private void fleetAtHome() {
        board.test.units(board.home, "space", nekro(), UnitType.Carrier, 2);
        board.test.units(board.home, "space", nekro(), UnitType.Dreadnought, 2);
        board.test.units(board.home, "mordaiii", nekro(), UnitType.Infantry, 6);
    }

    private void holds(Player owner, String planet, int infantry) {
        owner.addPlanet(planet);
        if (infantry > 0) board.test.units(system, planet, owner, UnitType.Infantry, infantry);
    }

    private void sardakkDestroyerWorthKilling() {
        board.test.points(sardakk(), 6);
        nekro().setSecret("sar");
        board.test.units(system, "space", sardakk(), UnitType.Destroyer, 1);
    }

    private Optional<TacticalPlan> attack() {
        return TacticalPlanner.forTarget(board.test.game, nekro(), target).filter(plan -> plan.kind() == Kind.ATTACK);
    }

    // Before, any system with a second other player in it got no attack plan at all. Sardakk's lone destroyer is
    // worth killing (sole leader, Spark a Rebellion); Sol's Starpoint, held by eight infantry, is not worth invading.
    // The plan fights Sardakk, claims the free New Albion and leaves Starpoint alone.
    @Test
    void fightsTheShipOwnerAndLeavesABystandersPlanetAlone() {
        fleetAtHome();
        sardakkDestroyerWorthKilling();
        holds(sol(), STARPOINT, FORTRESS);

        Optional<TacticalPlan> plan = attack();

        assertThat(plan).isPresent();
        assertThat(plan.get().landings()).containsKey(NEW_ALBION).doesNotContainKey(STARPOINT);
    }

    // The same fight with Sol's Starpoint left empty: once the space combat against Sardakk is won, spare infantry
    // take Sol's planet as well, so the plan carries both decisions.
    @Test
    void takesABystandersPlanetWhenThatIsWorthItToo() {
        fleetAtHome();
        sardakkDestroyerWorthKilling();
        holds(sol(), STARPOINT, 0);

        Optional<TacticalPlan> plan = attack();

        assertThat(plan).isPresent();
        assertThat(plan.get().landings()).containsKey(STARPOINT);
    }

    // No ships in the system: Sol holds Starpoint with one infantry (worth taking) and Sardakk holds New Albion with
    // eight (not worth it). The plan invades Starpoint only.
    @Test
    void takesThePlanetWorthTakingAndNotTheOther() {
        fleetAtHome();
        holds(sol(), STARPOINT, 1);
        holds(sardakk(), NEW_ALBION, FORTRESS);

        Optional<TacticalPlan> plan = attack();

        assertThat(plan).isPresent();
        assertThat(plan.get().landings()).containsKey(STARPOINT).doesNotContainKey(NEW_ALBION);
    }

    // Ships of two other players in one system: the evaluator models a space combat between two players only
    // (LRR 78), so this stays refused.
    @Test
    void refusesASystemWithShipsOfTwoOtherPlayers() {
        fleetAtHome();
        sardakkDestroyerWorthKilling();
        board.test.units(system, "space", sol(), UnitType.Destroyer, 1);

        assertThat(attack()).isEmpty();
    }

    // A lone destroyer with nothing at stake is not worth an action, and two fortress planets of a player far behind
    // do not change that: still no attack.
    @Test
    void staysRefusedWhenNeitherFightIsWorthIt() {
        fleetAtHome();
        board.test.units(system, "space", sardakk(), UnitType.Destroyer, 1);
        holds(sol(), STARPOINT, FORTRESS);
        holds(sol(), NEW_ALBION, FORTRESS);

        assertThat(attack()).isEmpty();
    }
}
