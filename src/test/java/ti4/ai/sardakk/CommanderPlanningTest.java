package ti4.ai.sardakk;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.ai.sardakk.SardakkTestBoard.FIRST_SOURCE_PLANET;
import static ti4.ai.sardakk.SardakkTestBoard.SECOND_SOURCE_PLANET;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.tactical.TacticalPlan;
import ti4.ai.tactical.TacticalPlan.Kind;
import ti4.ai.tactical.TacticalPlanner;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

class CommanderPlanningTest extends BaseTi4Test {

    private SardakkTestBoard board;

    @BeforeEach
    void setUp() {
        board = new SardakkTestBoard();
    }

    private Optional<TacticalPlan> planForTarget() {
        return TacticalPlanner.forTarget(board.test.game, board.test.nekro, board.targetPosition());
    }

    // Infantry sit on the two planets next door with no ship anywhere: only G'hom Sek'kus can carry one of them to
    // the free planet.
    @Test
    void settlesAFreePlanetFromAnAdjacentStackWithoutAnyShip() {
        board.unlockCommander();
        board.infantryOnEachSourcePlanet(2);

        Optional<TacticalPlan> plan = planForTarget();

        assertThat(plan).isPresent();
        assertThat(plan.get().kind()).isEqualTo(Kind.EXPAND);
        assertThat(plan.get().moves()).isEmpty();
        assertThat(plan.get().landings()).containsEntry("lodor", 1);
    }

    @Test
    void doesNothingWithoutTheCommander() {
        board.infantryOnEachSourcePlanet(2);

        assertThat(planForTarget()).isEmpty();
    }

    // The commander cannot reach out of a system that holds the seat's own command token.
    @Test
    void ignoresASourceUnderItsOwnCommandToken() {
        board.unlockCommander();
        board.infantryOnEachSourcePlanet(2);
        board.tokenOnSource();

        assertThat(planForTarget()).isEmpty();
    }

    // Sol holds Lodor with a single infantry and no ship. One force from each of the two planets next door beats it,
    // although nothing travels by ship.
    @Test
    void invadesAWeaklyHeldPlanetFromAdjacentStacksWithoutAnyShip() {
        board.unlockCommander();
        board.infantryOnEachSourcePlanet(3);
        board.test.sol.addPlanet("lodor");
        board.test.units(board.target, "lodor", board.test.sol, UnitType.Infantry, 1);
        board.test.points(board.test.sol, 8);

        Optional<TacticalPlan> plan = planForTarget();

        assertThat(plan).isPresent();
        assertThat(plan.get().kind()).isEqualTo(Kind.ATTACK);
        assertThat(plan.get().moves()).isEmpty();
        assertThat(plan.get().landings()).containsKey("lodor");
    }

    @Test
    void doesNotInvadeWithoutTheCommanderOrAShip() {
        board.infantryOnEachSourcePlanet(3);
        board.test.sol.addPlanet("lodor");
        board.test.units(board.target, "lodor", board.test.sol, UnitType.Infantry, 1);
        board.test.points(board.test.sol, 8);

        assertThat(planForTarget()).isEmpty();
    }

    // The commander commits only 1 ground force from each planet, so a single defender it cannot beat stays.
    @Test
    void doesNotInvadeAStackOneForcePerPlanetCannotBeat() {
        board.unlockCommander();
        board.infantryOnEachSourcePlanet(3);
        board.test.sol.addPlanet("lodor");
        board.test.units(board.target, "lodor", board.test.sol, UnitType.Infantry, 5);
        board.test.points(board.test.sol, 8);

        assertThat(planForTarget()).isEmpty();
    }

    @Test
    void usesNothingWhereTheSeatHasNoForces() {
        board.unlockCommander();
        board.test.units(board.source, FIRST_SOURCE_PLANET, board.test.sol, UnitType.Infantry, 1);

        assertThat(CommanderReach.pulls(board.test.game, board.test.nekro, board.target, (tile, planet) -> 0))
                .isEmpty();
    }

    @Test
    void offersOneForceFromEachPlanetAndKeepsTheGarrison() {
        board.unlockCommander();
        board.infantryOnEachSourcePlanet(3);
        board.test.units(board.source, FIRST_SOURCE_PLANET, board.test.nekro, UnitType.Mech, 1);

        List<CommanderReach.Pull> open =
                CommanderReach.pulls(board.test.game, board.test.nekro, board.target, (tile, planet) -> 0);
        List<CommanderReach.Pull> garrisoned =
                CommanderReach.pulls(board.test.game, board.test.nekro, board.target, (tile, planet) -> 4);
        List<CommanderReach.Pull> mechsHold =
                CommanderReach.pulls(board.test.game, board.test.nekro, board.target, (tile, planet) -> 1);

        assertThat(open)
                .containsExactlyInAnyOrder(
                        new CommanderReach.Pull(board.sourcePosition(), FIRST_SOURCE_PLANET, UnitType.Infantry),
                        new CommanderReach.Pull(board.sourcePosition(), SECOND_SOURCE_PLANET, UnitType.Infantry));
        assertThat(garrisoned).isEmpty();
        assertThat(mechsHold).hasSize(2);
    }
}
