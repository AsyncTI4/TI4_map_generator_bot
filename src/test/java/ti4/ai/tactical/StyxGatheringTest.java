package ti4.ai.tactical;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.eval.BoardView;
import ti4.ai.tactical.TacticalPlan.UnitMove;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

// Styx is guarded by two neutral dreadnoughts, a destroyer and three infantry. A fleet that cannot beat them yet still
// gathers toward Styx: its future strike earns a share of the prize that grows with the space-win chance the group and
// the ships able to join it next round would have, so a march forms before the fleet is already strong enough.
class StyxGatheringTest extends BaseTi4Test {

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

    private Player neutral() {
        return game().getNeutral();
    }

    private double futureFrom(Tile staging, int infantry) {
        TacticalPlanner.Context context = new TacticalPlanner.Context(game(), fracture.nekro());
        List<UnitMove> cargo = infantry == 0
                ? List.of()
                : List.of(new UnitMove(staging.getPosition(), BoardView.SPACE, UnitType.Infantry, infantry));
        return TacticalPlanner.futureScore(context, styx(), neutral(), staging, List.of(), cargo);
    }

    private double winningFuture() {
        FractureScenario strong = new FractureScenario();
        Tile beside = strong.fracture("frac3");
        strong.ships(beside, UnitType.Dreadnought, 3);
        strong.ships(beside, UnitType.Carrier, 2);
        TacticalPlanner.Context context = new TacticalPlanner.Context(strong.board.test.game, strong.nekro());
        List<UnitMove> cargo = List.of(new UnitMove(beside.getPosition(), BoardView.SPACE, UnitType.Infantry, 6));
        return TacticalPlanner.futureScore(
                context,
                strong.fracture(FractureScenario.STYX),
                strong.board.test.game.getNeutral(),
                beside,
                List.of(),
                cargo);
    }

    // Two dreadnoughts and a carrier win the fight for Styx about two times in five: short of the gate, but part-way.
    @Test
    void aGatheringPartWayToWinningEarnsPartOfThePrize() {
        Tile beside = fracture.fracture("frac3");
        fracture.ships(beside, UnitType.Dreadnought, 2);
        fracture.ships(beside, UnitType.Carrier, 1);

        double partial = futureFrom(beside, 4);

        assertThat(partial).isGreaterThan(TacticalPlanner.NEUTRAL_ATTACK_MIN_SCORE);
        assertThat(partial).isLessThan(winningFuture());
    }

    // A dreadnought and a carrier are far from winning, but still earn a little (user ruling 2026-11-21): the more a
    // seat
    // gathers, the better its chance, so only a gathering with no chance at all earns nothing.
    @Test
    void aWeakGatheringEarnsLittle() {
        Tile beside = fracture.fracture("frac3");
        fracture.ships(beside, UnitType.Dreadnought, 1);
        fracture.ships(beside, UnitType.Carrier, 1);

        double weak = futureFrom(beside, 4);

        fracture.ships(beside, UnitType.Dreadnought, 1);

        assertThat(weak).isPositive().isLessThan(futureFrom(beside, 4));
    }

    // Without ground forces the gathering cannot land this time, but the home dock can build infantry for its
    // carrier to fetch, so it still earns a smaller share than the same fleet that carries them.
    @Test
    void aGatheringWithoutGroundForcesEarnsLessThanOneCarryingThem() {
        Tile beside = fracture.fracture("frac3");
        fracture.ships(beside, UnitType.Dreadnought, 2);
        fracture.ships(beside, UnitType.Carrier, 1);

        double empty = futureFrom(beside, 0);

        assertThat(empty).isPositive().isLessThan(futureFrom(beside, 4));
    }

    // Two dreadnoughts alone are far from winning, but a carrier beside Styx on its other side (frac5) can join their
    // strike next round, and together they are further along. A ship two moves from Styx cannot join that strike.
    @Test
    void shipsThatCanJoinNextRoundCountTowardTheGathering() {
        Tile beside = fracture.fracture("frac3");
        fracture.ships(beside, UnitType.Dreadnought, 2);
        double alone = futureFrom(beside, 4);

        fracture.ships(fracture.fracture("frac5"), UnitType.Carrier, 1);

        assertThat(futureFrom(beside, 4)).isGreaterThan(alone);
    }

    @Test
    void groundForcesThatCanJoinNextRoundCountTowardLanding() {
        Tile beside = fracture.fracture("frac3");
        fracture.ships(beside, UnitType.Dreadnought, 3);
        fracture.ships(beside, UnitType.Carrier, 1);
        Tile other = fracture.fracture("frac5");
        fracture.ships(other, UnitType.Carrier, 1);
        double emptyJoiner = futureFrom(beside, 0);

        fracture.ships(other, UnitType.Infantry, 4);

        assertThat(futureFrom(beside, 0)).isGreaterThan(emptyJoiner);
    }

    // LRR 95.1: ships moving out of the staging system pick up the ground forces already there.
    @Test
    void infantryAlreadyAtTheStagingSystemCountTowardTheLanding() {
        Tile beside = fracture.fracture("frac3");
        fracture.ships(beside, UnitType.Dreadnought, 2);
        fracture.ships(beside, UnitType.Carrier, 1);
        double without = futureFrom(beside, 0);

        fracture.ships(beside, UnitType.Infantry, 4);

        assertThat(futureFrom(beside, 0)).isGreaterThan(without);
    }

    // Crossing the win gate never makes a fleet worth less than it was worth while it was still gathering.
    @Test
    void aFleetPastTheGateKeepsItsGatheringValue() {
        Tile beside = fracture.fracture("frac3");
        fracture.ships(beside, UnitType.Dreadnought, 2);
        fracture.ships(beside, UnitType.Carrier, 1);
        double gathering = futureFrom(beside, 4);

        fracture.ships(beside, UnitType.Dreadnought, 2);

        assertThat(futureFrom(beside, 4)).isGreaterThanOrEqualTo(gathering);
    }

    @Test
    void aLoneCarrierAndCruiserStillEarnALittle() {
        Tile beside = fracture.fracture("frac3");
        fracture.ships(beside, UnitType.Carrier, 1);
        fracture.ships(beside, UnitType.Cruiser, 1);

        assertThat(futureFrom(beside, 4)).isPositive();
    }

    // Four dreadnoughts on the ingress fill a fleet pool of four, and the carrier with the infantry waits on Cocytus.
    // Both reach frac2 this action. The gathering there keeps the carrier and its infantry and leaves a dreadnought
    // behind, instead of gathering only warships that could never take the planet.
    @Test
    void aGatheringKeepsAHaulerWhenTheWarshipsFillTheSupply() {
        fracture.nekro().setFleetCC(4);
        fracture.ships(fracture.ingress, UnitType.Dreadnought, 4);
        Tile cocytus = fracture.fracture(FractureScenario.COCYTUS);
        fracture.ships(cocytus, UnitType.Carrier, 1);
        fracture.ships(cocytus, UnitType.Infantry, 4);
        TacticalPlanner.Context context = new TacticalPlanner.Context(game(), fracture.nekro());

        TacticalPlanner.Gathering gathering = TacticalPlanner.gather(
                        context, styx(), fracture.fracture("frac2"), TacticalPlanner.stageLead(neutral(), styx()))
                .orElseThrow();

        assertThat(gathering.cargo()).isNotEmpty();
        assertThat(gathering.ships().stream()
                        .filter(move -> move.type() != UnitType.Fighter)
                        .mapToInt(UnitMove::count)
                        .sum())
                .isLessThanOrEqualTo(4);
    }

    @Test
    void joinersFillOnlyTheFleetSupplyLeft() {
        Tile beside = fracture.fracture("frac3");
        fracture.ships(beside, UnitType.Dreadnought, 2);
        fracture.ships(fracture.fracture("frac2"), UnitType.Carrier, 2);
        fracture.nekro().setFleetCC(3);
        TacticalPlanner.Context context = new TacticalPlanner.Context(game(), fracture.nekro());

        var joined = TacticalPlanner.withJoiners(
                context, styx(), beside, List.of(), BoardView.ships(BoardView.space(beside), fracture.nekro()));

        assertThat(joined).containsEntry(UnitType.Dreadnought, 2).containsEntry(UnitType.Carrier, 1);
    }

    // The fleet that wins two fights in five does not attack Styx yet, but it starts the march from the ingress
    // instead of waiting there until it is already strong enough.
    @Test
    void aFleetPartWayToWinningMarchesTowardStyx() {
        fracture.ships(fracture.ingress, UnitType.Dreadnought, 2);
        fracture.ships(fracture.ingress, UnitType.Carrier, 1);
        fracture.ships(fracture.ingress, UnitType.Infantry, 4);

        List<TacticalPlan> marches = TacticalPlanner.ranked(game(), fracture.nekro()).stream()
                .filter(plan -> TacticalPlanner.isMarchStep(game(), fracture.nekro(), plan))
                .toList();

        assertThat(marches).isNotEmpty();
        assertThat(marches.getFirst().score()).isPositive();
    }
}
