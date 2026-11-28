package ti4.ai.tactical;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.tactical.TacticalPlan.Kind;
import ti4.ai.tactical.TacticalPlan.UnitMove;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.model.MapPositionMapper;
import ti4.testUtils.BaseTi4Test;

// Gravity rifts are priced paths, never walls (LRR 41). A ship that leaves a rift survives with 0.7, so a plan's gain
// is scaled by the chance every rolling ship arrives and the expected loss of ships and cargo is subtracted.
class RiftPlanTest extends BaseTi4Test {

    private AiTestGame test;
    private String middle;
    private String beyond;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.game.setRound(3);
        test.nekroHome();
        middle = AiTestGame.neighbourOf(AiTestGame.HOME);
        beyond = MapPositionMapper.getAdjacentTilePositions(middle).stream()
                .filter(position -> !"x".equals(position) && !AiTestGame.HOME.equals(position))
                .filter(position -> !MapPositionMapper.getAdjacentTilePositions(AiTestGame.HOME)
                        .contains(position))
                .findFirst()
                .orElseThrow();
        test.place("26", beyond);
    }

    // The same expansion from a rift and from a plain system: the rift one keeps 0.7 of its gain and loses 0.3 of the
    // carrier (3) and its infantry (0.5).
    @Test
    void aRiftPlanScoresBelowTheSameCalmPlan() {
        TacticalPlan plan = new TacticalPlan(
                Kind.EXPAND,
                beyond,
                List.of(
                        new UnitMove(middle, "space", UnitType.Carrier, 1),
                        new UnitMove(middle, "space", UnitType.Infantry, 1)),
                Map.of("lodor", 1),
                2.0);
        Tile parking = test.place("47", middle);
        test.units(parking, "space", test.nekro, UnitType.Carrier, 1);
        test.units(parking, "space", test.nekro, UnitType.Infantry, 1);
        assertThat(TacticalPlanner.riftPriced(new TacticalPlanner.Context(test.game, test.nekro), plan)
                        .score())
                .isEqualTo(2.0);

        Tile rift = test.place("41", middle);
        test.units(rift, "space", test.nekro, UnitType.Carrier, 1);
        test.units(rift, "space", test.nekro, UnitType.Infantry, 1);
        assertThat(TacticalPlanner.riftPriced(new TacticalPlanner.Context(test.game, test.nekro), plan)
                        .score())
                .isCloseTo(0.7 * 2.0 - 0.3 * (3 + 0.5), offset(1e-9));
    }

    // A carrier parked in a rift cannot leave without rolling, so it still expands when nothing calm reaches.
    @Test
    void aCheapShipCrossesARiftWhenNothingCalmReaches() {
        Tile rift = test.place("41", middle);
        test.units(rift, "space", test.nekro, UnitType.Carrier, 1);
        test.units(rift, "space", test.nekro, UnitType.Infantry, 2);

        TacticalPlan plan =
                TacticalPlanner.forTarget(test.game, test.nekro, beyond).orElseThrow();

        assertThat(plan.kind()).isEqualTo(Kind.EXPAND);
        assertThat(plan.moves()).contains(new UnitMove(middle, "space", UnitType.Carrier, 1));
        assertThat(plan.score()).isPositive();
    }

    // A carrier two systems out reaches the system beyond the rift only by crossing it (+1, one roll, LRR 41.1-41.2).
    // The rift is a priced path, never a wall, so the expansion is still offered with the roll priced in.
    @Test
    void aCarrierCrossesARiftInThePathWhenNothingCalmReaches() {
        test.place("41", middle);
        String start = MapPositionMapper.getAdjacentTilePositions(middle).stream()
                .filter(position -> !"x".equals(position) && !AiTestGame.HOME.equals(position))
                .filter(position -> !position.equals(beyond))
                .filter(position ->
                        !MapPositionMapper.getAdjacentTilePositions(beyond).contains(position))
                .findFirst()
                .orElseThrow();
        Tile origin = test.place("19", start);
        test.units(origin, "space", test.nekro, UnitType.Carrier, 1);
        test.units(origin, "space", test.nekro, UnitType.Infantry, 2);

        TacticalPlan plan =
                TacticalPlanner.forTarget(test.game, test.nekro, beyond).orElseThrow();

        assertThat(plan.kind()).isEqualTo(Kind.EXPAND);
        assertThat(plan.moves()).contains(new UnitMove(start, "space", UnitType.Carrier, 1));
        assertThat(ti4.ai.eval.MoveBudget.riftExits(
                        test.game,
                        test.nekro,
                        test.game.getTileByPosition(beyond),
                        TacticalPlanner.departuresOf(plan.moves())))
                .isNotEmpty();
    }

    // Two cruisers beside an enemy destroyer and two dreadnoughts plus a carrier parked in the rift beside it: the
    // attack picked is never worth less, after the rift is priced, than the calm cruisers alone.
    @Test
    void anAttackNeverTradesACalmStrikeForAWorseRiftOne() {
        String calm = MapPositionMapper.getAdjacentTilePositions(beyond).stream()
                .filter(position -> !"x".equals(position) && !position.equals(middle))
                .filter(position -> !AiTestGame.HOME.equals(position))
                .findFirst()
                .orElseThrow();
        Tile cruisers = test.place("19", calm);
        test.units(cruisers, "space", test.nekro, UnitType.Cruiser, 2);
        test.units(test.game.getTileByPosition(beyond), "space", test.sol, UnitType.Destroyer, 1);
        java.util.Optional<TacticalPlan> calmOnly = TacticalPlanner.forTarget(test.game, test.nekro, beyond);

        Tile rift = test.place("41", middle);
        test.units(rift, "space", test.nekro, UnitType.Dreadnought, 2);
        test.units(rift, "space", test.nekro, UnitType.Carrier, 1);
        java.util.Optional<TacticalPlan> chosen = TacticalPlanner.forTarget(test.game, test.nekro, beyond);

        calmOnly.ifPresent(found -> assertThat(chosen).isPresent());
        calmOnly.ifPresent(
                found -> assertThat(chosen.orElseThrow().score()).isGreaterThanOrEqualTo(found.score() - 1e-9));
    }

    @Test
    void shipsParkedInARiftAreOrigins() {
        Tile rift = test.place("41", middle);
        test.units(rift, "space", test.nekro, UnitType.Destroyer, 1);

        assertThat(new TacticalPlanner.Context(test.game, test.nekro).origins()).contains(rift);
    }
}
