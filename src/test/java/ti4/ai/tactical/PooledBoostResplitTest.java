package ti4.ai.tactical;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiExperiments;
import ti4.ai.AiExperiments.Experiment;
import ti4.ai.AiTestGame;
import ti4.ai.eval.BoardView;
import ti4.ai.eval.DecidingSeat;
import ti4.ai.eval.FleetLimit;
import ti4.ai.tactical.TacticalPlan.Kind;
import ti4.ai.tactical.TacticalPlan.UnitMove;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.model.MapPositionMapper;
import ti4.testUtils.BaseTi4Test;

// Gravity Drive gives +1 move to one ship per tactical action, shared by every starting system. Each system's own best
// departure spends that boost on its slow ship (dreadnought or carrier, move 1, two systems away), so the plain merge
// of two such departures needs two boosts and is dropped. The re-split (experiment POOLED_BOOST_RESPLIT) allocates the
// one boost over the union of both systems' ships: one slow ship is boosted and only the other system's fast ships
// come.
// The lanes are built like GravityDriveOncePerActionTest's: empty systems that meet only at the target.
class PooledBoostResplitTest extends BaseTi4Test {

    private static final String TARGET = "104";

    private AiTestGame test;
    private Tile target;
    private Tile first;
    private Tile second;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.game.setRound(4);
        test.nekroHome();
        test.nekro.setTacticalCC(3);
        test.nekro.setFleetCC(12);
        target = test.place("27", TARGET);
        first = lane(TARGET, 2);
        second = lane(TARGET, 2);
        test.units(target, "space", test.sol, UnitType.Cruiser, 2);
        test.units(target, "space", test.sol, UnitType.Destroyer, 2);
        test.points(test.sol, 6);
        test.nekro.addTech("gd");
    }

    private Tile lane(String from, int steps) {
        String position = from;
        for (int step = 0; step < steps; step++) {
            String previous = position;
            position = MapPositionMapper.getAdjacentTilePositions(previous).stream()
                    .filter(candidate -> !"x".equals(candidate))
                    .filter(candidate -> test.game.getTileByPosition(candidate) == null)
                    .filter(candidate -> MapPositionMapper.getAdjacentTilePositions(candidate).stream()
                            .noneMatch(next -> !next.equals(previous) && test.game.getTileByPosition(next) != null))
                    .findFirst()
                    .orElseThrow();
            test.place("46", position);
        }
        return test.game.getTileByPosition(position);
    }

    // Lane one: a dreadnought and two destroyers. Lane two: a carrier and two cruisers. Both slow ships need the boost.
    private void slowAndFastFleets() {
        test.units(first, "space", test.nekro, UnitType.Dreadnought, 1);
        test.units(first, "space", test.nekro, UnitType.Destroyer, 2);
        test.units(second, "space", test.nekro, UnitType.Carrier, 1);
        test.units(second, "space", test.nekro, UnitType.Cruiser, 2);
    }

    // Lane one: a carrier with two cruisers and four infantry. Lane two: a dreadnought and three destroyers. Each lane
    // alone is a poor attack; together, with the boost on one slow ship, they win the space combat and land.
    private void landingFleets() {
        test.units(first, "space", test.nekro, UnitType.Carrier, 1);
        test.units(first, "space", test.nekro, UnitType.Cruiser, 2);
        test.units(first, "space", test.nekro, UnitType.Infantry, 4);
        test.units(second, "space", test.nekro, UnitType.Dreadnought, 1);
        test.units(second, "space", test.nekro, UnitType.Destroyer, 3);
    }

    private static UnitMove ship(Tile origin, UnitType type, int count) {
        return new UnitMove(origin.getPosition(), "space", type, count);
    }

    private static int carriedFrom(List<UnitMove> moves) {
        return moves.stream()
                .filter(move -> move.type() == UnitType.Fighter || BoardView.GROUND_FORCES.contains(move.type()))
                .mapToInt(UnitMove::count)
                .sum();
    }

    private int capacityOf(List<UnitMove> moves) {
        return moves.stream()
                .filter(move -> BoardView.MOVING_SHIPS.contains(move.type()))
                .mapToInt(move -> move.count() * BoardView.capacity(test.nekro, move.type()))
                .sum();
    }

    private void disableResplitFor(Player seat) {
        AiExperiments.disable(seat, Set.of(Experiment.POOLED_BOOST_RESPLIT));
    }

    private TacticalPlanner.Context context() {
        return new TacticalPlanner.Context(test.game, test.nekro);
    }

    private Optional<TacticalPlan> attack() {
        return TacticalPlanner.forTarget(test.game, test.nekro, TARGET).filter(plan -> plan.kind() == Kind.ATTACK);
    }

    private static long startingSystems(List<UnitMove> moves) {
        return moves.stream()
                .filter(move -> move.type() != UnitType.Fighter)
                .filter(move -> BoardView.MOVING_SHIPS.contains(move.type()))
                .map(UnitMove::origin)
                .filter(origin -> !TARGET.equals(origin))
                .distinct()
                .count();
    }

    private void assertPoolInvariant(TacticalPlanner.Context context, List<TacticalPlanner.Candidate> pool) {
        assertThat(pool).allSatisfy(candidate -> {
            assertThat(context.withinMoveBoosts(target, candidate.moves())).isTrue();
            assertThat(TacticalPlanner.fleetSlotsAfter(context, target, candidate.moves()))
                    .isLessThanOrEqualTo(FleetLimit.ships(test.nekro));
        });
    }

    @Test
    void theLanesAreTwoSystemsLong() {
        TacticalPlanner.Context context = context();

        assertThat(context.distance(first, target, UnitType.Destroyer)).isEqualTo(2);
        assertThat(context.distance(second, target, UnitType.Destroyer)).isEqualTo(2);
    }

    @Test
    void aTwoSystemStrikeBoostsExactlyOneShip() {
        landingFleets();

        TacticalPlan plan = attack().orElseThrow();

        assertThat(startingSystems(plan.moves())).isEqualTo(2);
        assertThat(context().moveBoostsNeeded(target, plan.moves())).isEqualTo(1);
        assertThat(context().withinMoveBoosts(target, plan.moves())).isTrue();
    }

    @Test
    void aControlSeatKeepsTodaysSingleSystemStrike() {
        landingFleets();
        disableResplitFor(test.nekro);
        TacticalPlanner.Context context = context();

        List<TacticalPlanner.Candidate> pool = TacticalPlanner.candidatePool(context, target, test.sol);

        // Without the re-split the two boosted departures never merge, so no candidate leaves from both lanes, and the
        // attack the enabled seat makes from both lanes is not made.
        assertThat(pool).isNotEmpty();
        assertThat(pool).allMatch(candidate -> startingSystems(candidate.moves()) == 1);
        attack().ifPresent(plan -> assertThat(startingSystems(plan.moves())).isEqualTo(1));
    }

    @Test
    void theEnabledPoolHasATwoSystemCandidateTheControlPoolLacks() {
        slowAndFastFleets();
        TacticalPlanner.Context enabled = context();
        List<TacticalPlanner.Candidate> treatment = TacticalPlanner.candidatePool(enabled, target, test.sol);
        disableResplitFor(test.nekro);
        TacticalPlanner.Context disabled = context();
        List<TacticalPlanner.Candidate> control = TacticalPlanner.candidatePool(disabled, target, test.sol);

        assertThat(treatment).anyMatch(candidate -> startingSystems(candidate.moves()) == 2);
        assertThat(control).noneMatch(candidate -> startingSystems(candidate.moves()) == 2);
        // The singles are the same either way: only the merge step differs.
        assertThat(control.stream().map(TacticalPlanner.Candidate::moves).toList())
                .isSubsetOf(
                        treatment.stream().map(TacticalPlanner.Candidate::moves).toList());
    }

    @Test
    void everyPooledCandidateStaysWithinBoostsAndFleetSupply() {
        slowAndFastFleets();
        TacticalPlanner.Context context = context();

        assertPoolInvariant(context, TacticalPlanner.candidatePool(context, target, test.sol));
    }

    @Test
    void aSmallFleetPoolStillHoldsThePoolInvariant() {
        slowAndFastFleets();
        test.nekro.setFleetCC(3);
        TacticalPlanner.Context context = context();

        List<TacticalPlanner.Candidate> pool = TacticalPlanner.candidatePool(context, target, test.sol);

        assertThat(pool).isNotEmpty();
        assertPoolInvariant(context, pool);
    }

    @Test
    void cargoAfterTheResplitStaysWithinCapacity() {
        landingFleets();
        TacticalPlanner.Context context = context();

        List<TacticalPlanner.Candidate> pool = TacticalPlanner.candidatePool(context, target, test.sol);

        assertThat(pool).anyMatch(candidate -> startingSystems(candidate.moves()) == 2);
        assertPoolInvariant(context, pool);
        for (TacticalPlanner.Candidate candidate : pool) {
            Map<String, List<UnitMove>> byOrigin = new HashMap<>();
            candidate
                    .moves()
                    .forEach(move -> byOrigin.computeIfAbsent(move.origin(), ignored -> new java.util.ArrayList<>())
                            .add(move));
            byOrigin.values().forEach(moves -> assertThat(carriedFrom(moves)).isLessThanOrEqualTo(capacityOf(moves)));
        }
    }

    // Two fleet tokens: the re-split keeps at most two non-fighter ships. If it drops the carrier, the infantry it
    // would
    // have carried stay behind instead of riding in a cruiser with no capacity.
    @Test
    void aShrunkDepartureCarriesOnlyWhatItsShipsHold() {
        landingFleets();
        test.nekro.setFleetCC(2);
        TacticalPlanner.Context context = context();
        List<TacticalPlanner.Part> parts = List.of(
                new TacticalPlanner.Part(
                        first,
                        new TacticalPlanner.Departure(
                                List.of(ship(first, UnitType.Carrier, 1), ship(first, UnitType.Cruiser, 2)),
                                List.of(ship(first, UnitType.Infantry, 4)),
                                false)),
                new TacticalPlanner.Part(
                        second,
                        new TacticalPlanner.Departure(
                                List.of(ship(second, UnitType.Dreadnought, 1), ship(second, UnitType.Destroyer, 3)),
                                List.of(),
                                false)));

        List<TacticalPlanner.Part> pooled = TacticalPlanner.pooled(context, target, parts);

        assertThat(pooled).isNotEmpty();
        List<UnitMove> moving = new java.util.ArrayList<>();
        for (TacticalPlanner.Part part : pooled) {
            assertThat(count(part.departure().cargo()))
                    .isLessThanOrEqualTo(capacityOf(part.departure().ships()));
            moving.addAll(part.departure().ships());
            moving.addAll(part.departure().cargo());
        }
        assertThat(moving.stream()
                        .filter(move -> BoardView.MOVING_SHIPS.contains(move.type()))
                        .mapToInt(UnitMove::count)
                        .sum())
                .isLessThanOrEqualTo(2);
        assertThat(context.withinMoveBoosts(target, moving)).isTrue();
    }

    // Without Fighter II fighters cannot move on their own, so the shared allocator is not offered them; they ride in
    // whatever capacity the re-split keeps, and never travel in a part that has no capacity.
    @Test
    void groundedFightersOnlyRideInKeptCapacity() {
        landingFleets();
        test.units(first, "space", test.nekro, UnitType.Fighter, 2);
        TacticalPlanner.Context context = context();
        List<TacticalPlanner.Part> parts = List.of(
                new TacticalPlanner.Part(
                        first,
                        new TacticalPlanner.Departure(
                                List.of(
                                        ship(first, UnitType.Carrier, 1),
                                        ship(first, UnitType.Cruiser, 2),
                                        ship(first, UnitType.Fighter, 2)),
                                List.of(ship(first, UnitType.Infantry, 2)),
                                false)),
                new TacticalPlanner.Part(
                        second,
                        new TacticalPlanner.Departure(
                                List.of(ship(second, UnitType.Dreadnought, 1), ship(second, UnitType.Destroyer, 3)),
                                List.of(),
                                false)));

        assertThat(ti4.ai.eval.FighterWings.flies(test.game, test.nekro)).isFalse();

        List<TacticalPlanner.Part> pooled = TacticalPlanner.pooled(context, target, parts);

        assertThat(pooled).isNotEmpty();
        List<UnitMove> moving = new java.util.ArrayList<>();
        for (TacticalPlanner.Part part : pooled) {
            List<UnitMove> all = new java.util.ArrayList<>(part.departure().ships());
            all.addAll(part.departure().cargo());
            assertThat(carriedFrom(all))
                    .isLessThanOrEqualTo(capacityOf(part.departure().ships()));
            moving.addAll(all);
        }
        assertThat(context.withinMoveBoosts(target, moving)).isTrue();
    }

    private static int count(List<UnitMove> moves) {
        return moves.stream().mapToInt(UnitMove::count).sum();
    }

    // Sol forecasts the Nekro strike on its fleet. The forecast runs as Nekro (DecidingSeat.of(enemy) in EnemyIntent),
    // so it follows the rival's flag, not the viewer's: with Nekro enabled it sees the pooled two-system strike, and
    // with Nekro disabled it sees the old forecast, where neither lane alone wins often enough to count. Sol's own flag
    // does not change it.
    @Test
    void enemyIntentForecastsThePooledStrikeOnlyForAnEnabledRival() {
        slowAndFastFleets();

        OptionalDouble pooled =
                DecidingSeat.of(test.sol, () -> EnemyIntent.attackWorth(test.game, test.nekro, target, test.sol));
        disableResplitFor(test.sol);
        EnemyIntent.forget();
        OptionalDouble viewerDisabled =
                DecidingSeat.of(test.sol, () -> EnemyIntent.attackWorth(test.game, test.nekro, target, test.sol));
        disableResplitFor(test.nekro);
        EnemyIntent.forget();
        OptionalDouble today =
                DecidingSeat.of(test.sol, () -> EnemyIntent.attackWorth(test.game, test.nekro, target, test.sol));

        assertThat(pooled).isPresent();
        assertThat(viewerDisabled).isEqualTo(pooled);
        assertThat(pooled.getAsDouble()).isGreaterThan(today.orElse(Double.NEGATIVE_INFINITY));
    }
}
