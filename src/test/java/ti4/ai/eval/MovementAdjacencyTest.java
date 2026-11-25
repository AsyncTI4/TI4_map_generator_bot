package ti4.ai.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.eval.MoveEnablers.Enabler;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.model.BorderAnomalyIds;
import ti4.model.MapPositionMapper;
import ti4.testUtils.BaseTi4Test;

// Movement effects that change which systems are adjacent or ignore anomalies: the action cards the AI can play for
// itself (Lost Star Chart, Nav Suite) and the enemy effects it must expect in threats (Spatial Conduit Cylinder,
// Emissary Taivra, Captain Mendosa, Memoria), plus Void Tether and Lazax Gate Folding.
class MovementAdjacencyTest extends BaseTi4Test {

    private static final ToDoubleFunction<UnitType> COST = type -> switch (type) {
        case Carrier, Cruiser -> 3;
        case Flagship -> 8;
        default -> 1;
    };

    private static <T> T nav(Player seat, Supplier<T> evaluation) {
        return MoveEnablers.assuming(seat, Enabler.NAV_SUITE, evaluation);
    }

    private static <T> T chart(Player seat, Supplier<T> evaluation) {
        return MoveEnablers.assuming(seat, Enabler.LOST_STAR_CHART, evaluation);
    }

    // Enemy shortcuts are only assumed when another seat (here Nekro) imagines the enemy's move.
    private static <T> T seenBy(Player viewer, Supplier<T> evaluation) {
        return DecidingSeat.of(viewer, evaluation);
    }

    // Lost Star Chart: systems with alpha and beta wormholes are adjacent to each other during this tactical action.
    @Test
    void lostStarChartLinksAlphaAndBeta() {
        MoveLine line = new MoveLine(2);
        line.swap(1, "26");
        String island = line.islandPosition();
        line.test.place("25", island);
        String alpha = line.positions.get(1);

        assertThat(MovementGraph.distances(line.test.game, line.test.nekro, alpha, 1))
                .doesNotContainKey(island);
        assertThat(chart(line.test.nekro, () -> MovementGraph.distances(line.test.game, line.test.nekro, alpha, 1)))
                .containsEntry(island, 1);
        assertThat(chart(line.test.nekro, () -> MovementGraph.distances(line.test.game, line.test.sol, alpha, 1)))
                .doesNotContainKey(island);
    }

    // Lost Star Chart I/α: under Enforced Travel Ban the card still links the systems, alpha to alpha included.
    @Test
    void lostStarChartLinksAlphaToAlphaUnderTravelBan() {
        MoveLine line = new MoveLine(2);
        line.swap(1, "39");
        String island = line.islandPosition();
        line.test.place("26", island);
        String alpha = line.positions.get(1);
        assertThat(MovementGraph.distances(line.test.game, line.test.nekro, alpha, 1))
                .containsEntry(island, 1);

        line.test.game.addLaw("travel_ban", null);
        assertThat(MovementGraph.distances(line.test.game, line.test.nekro, alpha, 1))
                .doesNotContainKey(island);
        assertThat(chart(line.test.nekro, () -> MovementGraph.distances(line.test.game, line.test.nekro, alpha, 1)))
                .containsEntry(island, 1);
    }

    // Nav Suite III and VI: ships move through and into asteroid fields and supernovas.
    @Test
    void navSuitePassesSupernovasAndAsteroids() {
        MoveLine line = new MoveLine(3);
        line.swap(1, "43");
        line.swap(2, "44");
        Player nekro = line.test.nekro;
        assertThat(MovementGraph.distances(line.test.game, nekro, AiTestGame.HOME, 3))
                .containsOnlyKeys(AiTestGame.HOME);

        assertThat(nav(nekro, () -> MovementGraph.distances(line.test.game, nekro, AiTestGame.HOME, 3)))
                .containsEntry(line.positions.get(1), 1)
                .containsEntry(line.positions.get(2), 2)
                .containsEntry(line.positions.get(3), 3);
    }

    // Nav Suite V: ships move through a nebula and ships leaving one keep their normal move value.
    @Test
    void navSuiteLiftsTheNebulaCap() {
        MoveLine line = new MoveLine(3);
        Tile nebula = line.swap(0, "42");
        line.swap(2, "42");
        Player nekro = line.test.nekro;
        assertThat(ShipSpeed.base(line.test.game, nekro, UnitType.Carrier, nebula))
                .isEqualTo(1);
        assertThat(MovementGraph.distances(line.test.game, nekro, AiTestGame.HOME, 3))
                .doesNotContainKey(line.positions.get(3));

        assertThat(nav(nekro, () -> ShipSpeed.base(line.test.game, nekro, UnitType.Cruiser, nebula)))
                .isEqualTo(2);
        assertThat(nav(nekro, () -> MovementGraph.distances(line.test.game, nekro, AiTestGame.HOME, 3)))
                .containsEntry(line.positions.get(3), 3);
    }

    // Nav Suite IV: ships cross a gravity rift without rolling, and without its +1.
    @Test
    void navSuiteCrossesARiftWithNoBonusAndNoRoll() {
        MoveLine line = new MoveLine(3);
        line.swap(1, "41");
        Player nekro = line.test.nekro;
        String far = line.positions.get(3);

        MovementGraph.Route plain = MovementGraph.route(line.test.game, nekro, AiTestGame.HOME, far, 3);
        assertThat(plain.rifting()).isEqualTo(2);
        assertThat(plain.calm()).isEqualTo(MovementGraph.NONE);

        MovementGraph.Route navigated =
                nav(nekro, () -> MovementGraph.route(line.test.game, nekro, AiTestGame.HOME, far, 3));
        assertThat(navigated.calm()).isEqualTo(3);
        assertThat(navigated.rifting()).isEqualTo(3);
    }

    // Void Tether I: other players treat the two systems across the tether as not adjacent; the Empyrean do not.
    @Test
    void voidTetherSplitsNeighboursForOthersOnly() {
        MoveLine line = new MoveLine(1);
        String beside = line.positions.get(1);
        int direction =
                MapPositionMapper.getAdjacentTilePositions(AiTestGame.HOME).indexOf(beside);
        line.test.game.addBorderAnomaly(AiTestGame.HOME, direction, BorderAnomalyIds.VOID_TETHER);
        Player empyrean = line.test.sol;
        empyrean.addBreakthrough("empyreanbt");
        empyrean.setBreakthroughUnlocked("empyreanbt", true);

        assertThat(Adjacency.forMovement(line.test.game, line.test.nekro, AiTestGame.HOME))
                .doesNotContain(beside);
        assertThat(Adjacency.forMovement(line.test.game, line.test.nekro, beside))
                .doesNotContain(AiTestGame.HOME);
        assertThat(Adjacency.forMovement(line.test.game, empyrean, AiTestGame.HOME))
                .contains(beside);
    }

    // Lazax Gate Folding: while the Winnu do not control Mecatol Rex it has an alpha and a beta wormhole for them,
    // so the link runs both ways.
    @Test
    void lazaxGateFoldingLinksMecatolBothWays() {
        MoveLine line = new MoveLine(1);
        line.swap(1, "26");
        line.test.place("18", "000");
        Player winnu = line.test.addSeat("300000000000000004", "winnu", "green");
        winnu.addTech("lgf");
        String alpha = line.positions.get(1);

        assertThat(Adjacency.forMovement(line.test.game, winnu, alpha)).contains("000");
        assertThat(Adjacency.forMovement(line.test.game, winnu, "000")).contains(alpha);
        assertThat(chart(winnu, () -> Adjacency.lostStarSystems(line.test.game, winnu)))
                .contains("000", alpha);
    }

    // Enforced Travel Ban: alpha and beta wormholes have no effect during movement, and they are all Lazax Gate Folding
    // lends Mecatol Rex, so the link is gone both ways.
    @Test
    void lazaxGateFoldingDoesNothingUnderEnforcedTravelBan() {
        MoveLine line = new MoveLine(1);
        line.swap(1, "26");
        line.test.place("18", "000");
        Player winnu = line.test.addSeat("300000000000000004", "winnu", "green");
        winnu.addTech("lgf");
        line.test.game.addLaw("travel_ban", null);
        String alpha = line.positions.get(1);

        assertThat(Adjacency.forMovement(line.test.game, winnu, alpha)).doesNotContain("000");
        assertThat(Adjacency.forMovement(line.test.game, winnu, "000")).doesNotContain(alpha);
    }

    // The engine forgets Lost Star Chart and Nav Suite once movement is done, so a second tactical action in the same
    // turn is never planned as if the card were still in effect.
    @Test
    void movementCardsEndWithTheMovement() {
        MoveLine line = new MoveLine(1);
        Player nekro = line.test.nekro;
        line.test.aiIsActive("action");
        line.test.game.setStoredValue("currentActionSummarynekro", " Activated " + line.positions.get(1) + ".");
        line.test.game.setStoredValue(MoveEnablers.NAV_SUITE_PLAYED, nekro.getFaction());
        assertThat(MoveEnablers.played(line.test.game, nekro, Enabler.NAV_SUITE))
                .isTrue();

        ti4.helpers.ButtonHelperTacticalAction.clearMovementEnablers(line.test.game);

        assertThat(MoveEnablers.played(line.test.game, nekro, Enabler.NAV_SUITE))
                .isFalse();
    }

    // Spatial Conduit Cylinder: after activating a system with their units, it is adjacent to every system with their
    // units. The AI expects an enemy to use it; it never assumes it for itself.
    @Test
    void anEnemyConduitReachesItsOwnUnitSystemInOne() {
        MoveLine line = new MoveLine(4);
        Player jolnar = line.test.sol;
        jolnar.addTech("scc");
        line.test.units(line.at(1), "space", jolnar, UnitType.Cruiser, 1);
        line.test.units(line.at(4), "space", jolnar, UnitType.Fighter, 1);
        String from = line.positions.get(1);
        String to = line.positions.get(4);

        assertThat(MovementGraph.route(line.test.game, jolnar, from, to, 2)).isNull();
        MovementGraph.Route conduit =
                seenBy(line.test.nekro, () -> MovementGraph.route(line.test.game, jolnar, from, to, 2));
        assertThat(conduit.calm()).isEqualTo(1);
        assertThat(conduit.rifting()).isEqualTo(1);
    }

    // Emissary Taivra: the activated system (with a non-delta wormhole) is adjacent to every other wormhole system,
    // so a ship reaches it from beside any wormhole.
    @Test
    void taivraJoinsTheTargetToEveryWormhole() {
        MoveLine line = new MoveLine(4);
        line.swap(2, "25");
        line.swap(4, "26");
        Player ghost = taivraHolder(line);
        ghost.addLeader("ghostagent");
        String from = line.positions.get(1);
        String to = line.positions.get(4);

        assertThat(MovementGraph.route(line.test.game, ghost, from, to, 2)).isNull();
        assertThat(seenBy(line.test.nekro, () -> MovementGraph.route(line.test.game, ghost, from, to, 2)))
                .isEqualTo(new MovementGraph.Route(2, 2));
        ghost.getLeaderByID("ghostagent").orElseThrow().setExhausted(true);
        assertThat(seenBy(line.test.nekro, () -> MovementGraph.route(line.test.game, ghost, from, to, 2)))
                .isNull();
    }

    // Emissary Taivra II: the target needs a non-delta wormhole, but a delta system is still one of the hubs.
    @Test
    void deltaIsATaivraHub() {
        MoveLine line = new MoveLine(4);
        line.swap(2, "17");
        line.swap(4, "26");
        Player ghost = taivraHolder(line);
        ghost.addLeader("ghostagent");
        String from = line.positions.get(1);

        assertThat(seenBy(
                        line.test.nekro,
                        () -> MovementGraph.route(line.test.game, ghost, from, line.positions.get(4), 2)))
                .isEqualTo(new MovementGraph.Route(2, 2));
        assertThat(seenBy(
                        line.test.nekro,
                        () -> MovementGraph.route(line.test.game, ghost, line.positions.get(4), from, 1)))
                .isNull();
    }

    // A seat holding Emissary Taivra. Creuss themselves link alpha and beta wormholes (Quantum Entanglement), so the
    // test hands the agent to Sol to see Taivra alone.
    private static Player taivraHolder(MoveLine line) {
        return line.test.sol;
    }

    // Captain Mendosa raises one ship's move to the fastest ship on the board: with a cruiser (move 2) on the board,
    // one
    // of two carriers (move 1) two systems out can reach, never both.
    @Test
    void mendosaLiftsOneEnemyShipOnly() {
        MoveLine line = new MoveLine(2);
        Player saar = line.test.addSeat("300000000000000006", "saar", "orange");
        saar.setTacticalCC(3);
        saar.addLeader("saaragent");
        line.test.units(line.at(0), "space", saar, UnitType.Carrier, 2);
        Tile elsewhere = line.test.place("27", line.islandPosition());
        line.test.units(elsewhere, "space", line.test.nekro, UnitType.Cruiser, 1);
        Tile target = line.at(2);
        Supplier<Map<String, Map<UnitType, Integer>>> threat =
                () -> MoveBudget.shipsWithin(line.test.game, saar, target, Journey.thisAction(), tile -> true, COST, 5);

        assertThat(threat.get()).isEmpty();
        assertThat(seenBy(line.test.nekro, threat)).containsEntry(AiTestGame.HOME, Map.of(UnitType.Carrier, 1));
    }

    // The Memoria still has to enter the mech's system before moving on, so a mech in an asteroid field is no
    // stepping stone for a flagship without Antimass Deflectors.
    @Test
    void memoriaCannotJumpIntoAnAsteroidFieldItCannotEnter() {
        MoveLine line = new MoveLine(5);
        Player nomad = line.test.addSeat("300000000000000007", "nomad", "yellow");
        nomad.setTacticalCC(3);
        nomad.removeOwnedUnitByID("nomad_flagship");
        nomad.addOwnedUnitByID("nomad_flagship2");
        line.test.units(line.at(1), "space", nomad, UnitType.Flagship, 1);
        line.swap(4, "44");
        line.test.units(line.at(4), "space", nomad, UnitType.Mech, 1);
        Tile target = line.at(5);

        assertThat(seenBy(
                        line.test.nekro,
                        () -> MoveBudget.shipsWithin(
                                line.test.game, nomad, target, Journey.thisAction(), tile -> true, COST, 5)))
                .isEmpty();
    }

    // Memoria: the Nomad flagship treats systems with their mechs as adjacent, so it arrives from beside a mech.
    @Test
    void memoriaReachesFromBesideAMech() {
        MoveLine line = new MoveLine(5);
        Player nomad = line.test.addSeat("300000000000000007", "nomad", "yellow");
        nomad.setTacticalCC(3);
        nomad.removeOwnedUnitByID("nomad_flagship");
        nomad.addOwnedUnitByID("nomad_flagship2");
        line.test.units(line.at(1), "space", nomad, UnitType.Flagship, 1);
        line.test.units(line.at(4), "space", nomad, UnitType.Mech, 1);
        Tile target = line.at(5);
        Supplier<Map<String, Map<UnitType, Integer>>> threat = () ->
                MoveBudget.shipsWithin(line.test.game, nomad, target, Journey.thisAction(), tile -> true, COST, 5);

        assertThat(threat.get()).isEmpty();
        assertThat(seenBy(line.test.nekro, threat)).containsKey(line.positions.get(1));
        assertThat(seenBy(line.test.nekro, threat).get(line.positions.get(1))).containsEntry(UnitType.Flagship, 1);
        assertThat(seenBy(
                        line.test.nekro,
                        () -> MoveBudget.feasible(
                                line.test.game,
                                nomad,
                                target,
                                List.of(new Departure(line.positions.get(1), UnitType.Flagship, 1)))))
                .isTrue();
    }
}
