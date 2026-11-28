package ti4.ai.tactical;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import ti4.ai.tactical.TacticalPlan.Kind;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

// Activating a system that holds ships of two other players. A space combat is between two players (LRR 78), and
// while the seat and another player both have ships in the active system a space combat must be resolved, so after
// the first combat the second fleet still has to be fought before any invasion; the plan prices both fights. The
// engine starts the first combat against the first of those players in seat order, so that is the order planned:
// the second fight starts with what survived the first, without a second Space Cannon Offense (LRR 77.2).
// Seats in order: Nekro (the AI), Sol, Sardakk, Xxcha. Sol's lone destroyer is worth killing: Sol leads and the seat
// holds Spark a Rebellion. The seat brings two carriers, two dreadnoughts and six infantry into tile 27.
class TwoFleetAttackTest extends BaseTi4Test {

    private DefenseScenario board;
    private Player xxcha;
    private String target;
    private Tile system;

    private void board() {
        board = new DefenseScenario();
        xxcha = board.test.addSeat("200000000000000004", "xxcha", "green");
        target = board.otherNeighbourOfHome();
        system = board.test.place("27", target);
        board.test.units(board.home, "space", board.test.nekro, UnitType.Carrier, 2);
        board.test.units(board.home, "space", board.test.nekro, UnitType.Dreadnought, 2);
        board.test.units(board.home, "mordaiii", board.test.nekro, UnitType.Infantry, 6);
        board.test.nekro.setSecret("sar");
    }

    private void leaderDestroyer(Player leader) {
        board.test.points(leader, 6);
        board.test.units(system, "space", leader, UnitType.Destroyer, 1);
    }

    private Optional<TacticalPlan> attack() {
        return TacticalPlanner.forTarget(board.test.game, board.test.nekro, target)
                .filter(plan -> plan.kind() == Kind.ATTACK);
    }

    @Test
    void fightsBothFleetsWhenBothAreBeatableAndTheWholeIsWorthIt() {
        board();
        leaderDestroyer(board.test.sol);
        board.test.units(system, "space", xxcha, UnitType.Cruiser, 1);

        assertThat(attack()).isPresent();
    }

    // Sol's destroyer alone is a clear attack; with three Xxcha dreadnoughts in the same system the second combat
    // cannot be skipped, and it makes the whole activation a loss.
    @Test
    void refusesTheWholePlanWhenTheSecondFleetIsTooStrong() {
        board();
        leaderDestroyer(board.test.sol);
        xxcha.addPlanet("starpoint");
        assertThat(attack()).isPresent();

        board();
        leaderDestroyer(board.test.sol);
        board.test.units(system, "space", xxcha, UnitType.Dreadnought, 3);

        assertThat(attack()).isEmpty();
    }

    // The same two fleets, swapped between seats: when the paying fight (the leader's destroyer) comes first it
    // happens whenever the fleet arrives; behind two dreadnoughts it only happens if that fight is won first.
    @Test
    void theOrderOfTheFightsChangesTheWorth() {
        board();
        leaderDestroyer(board.test.sol);
        board.test.units(system, "space", xxcha, UnitType.Dreadnought, 2);
        double leaderFirst = attack().orElseThrow().score();

        board();
        leaderDestroyer(xxcha);
        board.test.units(system, "space", board.test.sol, UnitType.Dreadnought, 2);
        double leaderSecond = attack().orElseThrow().score();

        assertThat(leaderFirst).isGreaterThan(leaderSecond);
    }

    // Three other fleets in one system are beyond what the planner chains: still refused.
    @Test
    void refusesShipsOfThreeOtherPlayers() {
        board();
        leaderDestroyer(board.test.sol);
        board.test.units(system, "space", xxcha, UnitType.Destroyer, 1);
        board.test.units(system, "space", board.sardakk, UnitType.Destroyer, 1);

        assertThat(attack()).isEmpty();
    }

    @Test
    void combatsFollowSeatOrder() {
        board();

        assertThat(SharedSystemAttack.combatOrder(board.test.game, List.of(xxcha, board.test.sol)))
                .containsExactly(board.test.sol, xxcha);
    }
}
