package ti4.ai.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.brain.TickMemo;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.model.MapPositionMapper;
import ti4.testUtils.BaseTi4Test;

class MovementGraphTest extends BaseTi4Test {

    private AiTestGame test;
    private String middle;
    private String beyond;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.nekroHome();
        middle = AiTestGame.neighbourOf(AiTestGame.HOME);
        beyond = MapPositionMapper.getAdjacentTilePositions(middle).stream()
                .filter(position -> !"x".equals(position) && !AiTestGame.HOME.equals(position))
                .filter(position -> !MapPositionMapper.getAdjacentTilePositions(AiTestGame.HOME)
                        .contains(position))
                .findFirst()
                .orElseThrow();
        Tile blocked = test.place("26", middle);
        test.place("25", beyond);
        test.units(blocked, "space", test.sol, UnitType.Destroyer, 1);
    }

    // Other players' ships stop movement, so with only one way through the far system is out of reach.
    @Test
    void stopsAtSystemsHoldingOtherPlayersShips() {
        assertThat(MovementGraph.distances(test.game, test.nekro, AiTestGame.HOME, 2))
                .doesNotContainKey(beyond);
    }

    // Light/Wave Deflector lets ships move through systems that contain other players' ships.
    @Test
    void movesThroughOtherPlayersShipsWithLightWaveDeflector() {
        test.nekro.addTech("lwd");

        assertThat(MovementGraph.distances(test.game, test.nekro, AiTestGame.HOME, 2))
                .containsEntry(beyond, 2);
    }

    // Aerie Hololattice: other players cannot move through systems with Argent structures, Light/Wave Deflector or not.
    @Test
    void aerieHololatticeBlocksEvenWithLightWaveDeflector() {
        test.nekro.addTech("lwd");
        assertThat(MovementGraph.distances(test.game, test.nekro, AiTestGame.HOME, 2))
                .containsEntry(beyond, 2);

        test.sol.addTech("ah");
        test.units(test.game.getTileByPosition(middle), "lodor", test.sol, UnitType.Pds, 1);

        assertThat(MovementGraph.distances(test.game, test.nekro, AiTestGame.HOME, 2))
                .containsEntry(middle, 1)
                .doesNotContainKey(beyond);
    }

    @Test
    void theLatticeHolderMovesThroughItsOwnStructures() {
        test.sol.addTech("ah");
        test.units(test.game.getTileByPosition(middle), "lodor", test.sol, UnitType.Pds, 1);
        test.units(test.game.getTileByPosition(AiTestGame.HOME), "space", test.sol, UnitType.Cruiser, 1);

        assertThat(MovementGraph.distances(test.game, test.sol, AiTestGame.HOME, 2))
                .containsEntry(beyond, 2);
    }

    // Ships cannot pass through a nebula, unless Voidborn or the Shared Research law lets them.
    @Test
    void voidbornAndSharedResearchPassThroughNebulae() {
        MoveLine line = new MoveLine(2);
        line.swap(1, "42");
        String far = line.positions.get(2);
        assertThat(MovementGraph.distances(line.test.game, line.test.nekro, AiTestGame.HOME, 3))
                .containsKey(line.positions.get(1))
                .doesNotContainKey(far);

        line.test.nekro.addAbility("voidborn");
        assertThat(MovementGraph.distances(line.test.game, line.test.nekro, AiTestGame.HOME, 3))
                .containsEntry(far, 2);
        assertThat(MovementGraph.distances(line.test.game, line.test.sol, AiTestGame.HOME, 3))
                .doesNotContainKey(far);

        line.test.game.addLaw("shared_research", null);
        assertThat(MovementGraph.distances(line.test.game, line.test.sol, AiTestGame.HOME, 3))
                .containsEntry(far, 2);
    }

    // The nebula's move-1 start is a ship's move value (ShipSpeed), not a limit on the graph.
    @Test
    void distancesFromANebulaAreNotCapped() {
        MoveLine line = new MoveLine(3);
        line.swap(0, "42");

        assertThat(MovementGraph.distances(line.test.game, line.test.nekro, AiTestGame.HOME, 3))
                .containsEntry(line.positions.get(3), 3);
    }

    // LRR 41.1: a ship moving out of a gravity rift gets +1, so a move-1 ship parked in one reaches two systems out.
    // Its roll is unavoidable, so the plain distances view shows it, while the calm route says no roll-free path.
    @Test
    void aShipInAGravityRiftLeavesWithOneMore() {
        MoveLine line = new MoveLine(2);
        line.swap(0, "41");

        assertThat(MovementGraph.distances(line.test.game, line.test.nekro, AiTestGame.HOME, 1))
                .containsEntry(line.positions.get(1), 1)
                .containsEntry(line.positions.get(2), 1);
        MovementGraph.Route route =
                MovementGraph.route(line.test.game, line.test.nekro, AiTestGame.HOME, line.positions.get(2), 1);
        assertThat(route.rifting()).isEqualTo(1);
        assertThat(route.calm()).isEqualTo(MovementGraph.NONE);
    }

    // A rift on the way is a shortcut that costs a roll: the plain view never volunteers it, the route offers it.
    @Test
    void aRiftInThePathIsOnlyARiskyShortcut() {
        MoveLine line = new MoveLine(3);
        line.swap(1, "41");

        assertThat(MovementGraph.distances(line.test.game, line.test.nekro, AiTestGame.HOME, 3))
                .containsEntry(line.positions.get(1), 1)
                .doesNotContainKeys(line.positions.get(2), line.positions.get(3));
        MovementGraph.Route route =
                MovementGraph.route(line.test.game, line.test.nekro, AiTestGame.HOME, line.positions.get(3), 3);
        assertThat(route.rifting()).isEqualTo(2);
        assertThat(route.calm()).isEqualTo(MovementGraph.NONE);
    }

    // Gravity rift note I: moving into a rift gives no bonus and no roll.
    @Test
    void movingIntoARiftDoesNotRoll() {
        MoveLine line = new MoveLine(2);
        line.swap(1, "41");

        MovementGraph.Route route =
                MovementGraph.route(line.test.game, line.test.nekro, AiTestGame.HOME, line.positions.get(1), 2);
        assertThat(route.calm()).isEqualTo(1);
        assertThat(route.rifting()).isEqualTo(1);
    }

    // The effective distance is never 0 for another system, even straight out of a rift.
    @Test
    void aRiftNeighbourIsNeverAtDistanceZero() {
        MoveLine line = new MoveLine(1);
        line.swap(0, "41");

        assertThat(MovementGraph.distances(line.test.game, line.test.nekro, AiTestGame.HOME, 2))
                .containsEntry(AiTestGame.HOME, 0)
                .containsEntry(line.positions.get(1), 1);
    }

    // Thunder's Edge clarification: the +1 counts once per ship however many rifts it crosses. With the Circlet
    // nothing rolls, so two rifts in a row still give only one bonus.
    @Test
    void theRiftBonusCountsOncePerMovement() {
        MoveLine line = new MoveLine(4);
        line.swap(1, "41");
        line.at(2).addToken("token_gravityrift.png", "space");
        line.test.nekro.addRelic("circletofthevoid");

        MovementGraph.Route route =
                MovementGraph.route(line.test.game, line.test.nekro, AiTestGame.HOME, line.positions.get(3), 4);
        assertThat(route.calm()).isEqualTo(2);
        assertThat(route.rifting()).isEqualTo(2);
    }

    // The Circlet keeps the rift bonus and skips the roll, so the shortcut is calm.
    @Test
    void theCircletKeepsTheRiftBonusWithoutARoll() {
        MoveLine line = new MoveLine(3);
        line.swap(1, "41");
        line.test.nekro.addRelic("circletofthevoid");

        assertThat(MovementGraph.distances(line.test.game, line.test.nekro, AiTestGame.HOME, 2))
                .containsEntry(line.positions.get(2), 1)
                .containsEntry(line.positions.get(3), 2);
    }

    // Two rolls leave a ship worth less than staying home, so a second rolling rift ends the search.
    @Test
    void aSecondRollIsNeverRouted() {
        MoveLine line = new MoveLine(3);
        line.swap(1, "41");
        line.at(2).addToken("token_gravityrift.png", "space");

        assertThat(MovementGraph.route(line.test.game, line.test.nekro, AiTestGame.HOME, line.positions.get(2), 3)
                        .rifting())
                .isEqualTo(1);
        assertThat(MovementGraph.route(line.test.game, line.test.nekro, AiTestGame.HOME, line.positions.get(3), 3))
                .isNull();
    }

    // A Dimensional Tear makes its system a gravity rift, but Vuil'raith ships do not roll there: their own dock
    // system is an ordinary origin and waypoint for them, and stays a rift for everyone else.
    @Test
    void vuilraithShipsLeaveAndCrossTheirOwnDimensionalTear() {
        MoveLine line = new MoveLine(2);
        Player cabal = line.test.addSeat("300000000000000003", "cabal", "red");
        Tile tear = line.at(1);
        line.test.units(tear, "wellon", cabal, UnitType.Spacedock, 1);
        line.test.units(tear, "space", cabal, UnitType.Cruiser, 1);
        line.test.units(line.home, "space", cabal, UnitType.Cruiser, 1);
        assertThat(tear.isGravityRift(line.test.game, cabal)).isTrue();

        assertThat(MovementGraph.distances(line.test.game, cabal, tear.getPosition(), 1))
                .containsKeys(AiTestGame.HOME, line.positions.get(2));
        // Crossing the Tear still gives its +1 (Dimensional Tear IV), so the far system is one move away.
        assertThat(MovementGraph.distances(line.test.game, cabal, AiTestGame.HOME, 2))
                .containsEntry(line.positions.get(2), 1);
        // Anyone else in the Tear may still leave it, with the bonus and a forced roll.
        assertThat(MovementGraph.distances(line.test.game, line.test.nekro, tear.getPosition(), 1))
                .containsKeys(AiTestGame.HOME, line.positions.get(2));
        assertThat(MovementGraph.distances(line.test.game, line.test.nekro, AiTestGame.HOME, 2))
                .doesNotContainKey(line.positions.get(2));
    }

    // Dimensional Tear IV: the Vuil'raith never roll for their own Tear, even with a rift token there too, and still
    // take the +1 leaving it.
    @Test
    void vuilraithDoNotRollLeavingTheirTearEvenWithARiftToken() {
        MoveLine line = new MoveLine(3);
        Player cabal = line.test.addSeat("300000000000000003", "cabal", "red");
        Tile tear = line.at(1);
        line.test.units(tear, "wellon", cabal, UnitType.Spacedock, 1);
        tear.addToken("token_gravityrift.png", "space");

        MovementGraph.Route through =
                MovementGraph.route(line.test.game, cabal, AiTestGame.HOME, line.positions.get(2), 2);
        assertThat(through.calm()).isEqualTo(1);
        assertThat(MovementGraph.distances(line.test.game, cabal, tear.getPosition(), 1))
                .containsEntry(line.positions.get(3), 1);
    }

    // The Circlet of the Void ignores the movement effects of every other anomaly.
    @Test
    void theCircletEntersAndCrossesSupernovasAndAsteroids() {
        MoveLine line = new MoveLine(3);
        line.swap(1, "43");
        line.swap(2, "44");
        assertThat(MovementGraph.distances(line.test.game, line.test.nekro, AiTestGame.HOME, 3))
                .containsOnlyKeys(AiTestGame.HOME);

        line.test.nekro.addRelic("circletofthevoid");
        assertThat(MovementGraph.distances(line.test.game, line.test.nekro, AiTestGame.HOME, 3))
                .containsEntry(line.positions.get(3), 3);
    }

    // Supernovae stop all movement (LRR 86.1). Magmus Reactor lets ships move into one, and a ship that may move into
    // an anomaly may also move through it unless the anomaly says otherwise.
    @Test
    void magmusReactorEntersAndPassesThroughSupernovas() {
        MoveLine line = new MoveLine(3);
        line.swap(1, "43");
        Player nekro = line.test.nekro;
        assertThat(MovementGraph.distances(line.test.game, nekro, AiTestGame.HOME, 3))
                .doesNotContainKey(line.positions.get(1))
                .doesNotContainKey(line.positions.get(2));

        nekro.addTech("mr");
        assertThat(MovementGraph.distances(line.test.game, nekro, AiTestGame.HOME, 3))
                .containsEntry(line.positions.get(1), 1)
                .containsEntry(line.positions.get(2), 2);
    }

    // Gashlai Physiology lets ships move through supernovas, not stop in them.
    @Test
    void gashlaiPassesThroughButCannotStopInASupernova() {
        MoveLine line = new MoveLine(3);
        line.swap(1, "43");
        line.test.nekro.addAbility("gashlai_physiology");

        assertThat(MovementGraph.distances(line.test.game, line.test.nekro, AiTestGame.HOME, 3))
                .doesNotContainKey(line.positions.get(1))
                .containsEntry(line.positions.get(2), 2);
    }

    // One search per origin serves every smaller budget in the same tick.

    @Test
    void aSmallerBudgetIsServedFromTheLargerSearch() {
        MoveLine line = new MoveLine(3);
        try (TickMemo.Open ignored = TickMemo.open(line.test.game)) {
            Map<String, Integer> wide = MovementGraph.distances(line.test.game, line.test.nekro, AiTestGame.HOME, 3);
            Map<String, Integer> near = MovementGraph.distances(line.test.game, line.test.nekro, AiTestGame.HOME, 1);

            assertThat(wide).containsEntry(line.positions.get(3), 3);
            assertThat(near).containsOnlyKeys(AiTestGame.HOME, line.positions.get(1));
        }
    }
}
