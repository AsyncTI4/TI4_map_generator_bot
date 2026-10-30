package ti4.ai.tactical;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.tactical.TacticalPlan.Kind;
import ti4.ai.tactical.TacticalPlan.UnitMove;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.model.MapPositionMapper;
import ti4.testUtils.BaseTi4Test;

// Capacity is only worth moving when it carries something. A carrier that arrives empty is wasted unless it goes
// somewhere to pick units up.
class CapacityUseTest extends BaseTi4Test {

    private static final String HOME_PLANET = "mordaiii";

    private final DefenseScenario board = new DefenseScenario();

    private Player nekro() {
        return board.test.nekro;
    }

    private Optional<TacticalPlan> best() {
        return TacticalPlanner.best(board.test.game, nekro());
    }

    private static boolean carriesNothing(TacticalPlan plan) {
        return plan.moves().stream()
                .noneMatch(move -> move.type() == UnitType.Infantry
                        || move.type() == UnitType.Mech
                        || move.type() == UnitType.Fighter);
    }

    private static boolean movesACarrier(TacticalPlan plan) {
        return plan.moves().stream().anyMatch(move -> move.type() == UnitType.Carrier);
    }

    private static boolean movesOnlyCarriers(TacticalPlan plan) {
        return plan.moves().stream().allMatch(move -> move.type() == UnitType.Carrier);
    }

    private static List<UnitMove> carrierFrom(String origin) {
        return List.of(new UnitMove(origin, "space", UnitType.Carrier, 1));
    }

    private String emptySystemBeside(String position, String... excluded) {
        List<String> skip = List.of(excluded);
        return MapPositionMapper.getAdjacentTilePositions(position).stream()
                .filter(candidate -> !"x".equals(candidate)
                        && !skip.contains(candidate)
                        && board.test.game.getTileByPosition(candidate) == null)
                .findFirst()
                .orElseThrow();
    }

    // Only a ship that is mostly capacity counts as a hauler: warships that also carry (a dreadnought, a war sun, a
    // flagship) are sent for their guns, and a cruiser or destroyer has no room to speak of.
    @Test
    void onlyAShipThatIsMostlyCapacityIsAHauler() {
        assertThat(CapacityUse.hauler(nekro(), UnitType.Carrier)).isTrue();
        assertThat(CapacityUse.hauler(nekro(), UnitType.Dreadnought)).isFalse();
        assertThat(CapacityUse.hauler(nekro(), UnitType.Warsun)).isFalse();
        assertThat(CapacityUse.hauler(nekro(), UnitType.Flagship)).isFalse();
        assertThat(CapacityUse.hauler(nekro(), UnitType.Cruiser)).isFalse();
        assertThat(CapacityUse.hauler(nekro(), UnitType.Destroyer)).isFalse();
    }

    // Two carriers and one infantry: the first carrier is in use, so only the second is wasted. A carrier with room
    // to spare but something aboard is not penalised, because there was nothing more to carry.
    @Test
    void onlyACarrierWithNothingAboardIsPenalised() {
        int capacity = ti4.ai.eval.BoardView.capacity(nekro(), UnitType.Carrier);
        List<UnitMove> twoCarriersOneInfantry = List.of(
                new UnitMove("301", "space", UnitType.Carrier, 2), new UnitMove("301", "space", UnitType.Infantry, 1));

        assertThat(CapacityUse.emptyHaulerPenalty(nekro(), twoCarriersOneInfantry))
                .isEqualTo(CapacityUse.EMPTY_SLOT_VALUE * capacity);
        assertThat(CapacityUse.emptyHaulerPenalty(nekro(), carrierFrom("301")))
                .isEqualTo(CapacityUse.EMPTY_SLOT_VALUE * capacity);
        assertThat(CapacityUse.emptyHaulerPenalty(
                        nekro(),
                        List.of(
                                new UnitMove("301", "space", UnitType.Carrier, 1),
                                new UnitMove("301", "space", UnitType.Infantry, 1))))
                .isZero();
    }

    // A carrier among warships is a fleet member and is not penalised; only a hauler moving with no other ship is.
    @Test
    void aCarrierInsideAFleetIsNotALoneHauler() {
        List<UnitMove> fleet = List.of(
                new UnitMove("301", "space", UnitType.Carrier, 1), new UnitMove("301", "space", UnitType.Cruiser, 2));

        assertThat(CapacityUse.loneHaulerPenalty(nekro(), fleet)).isZero();
        assertThat(CapacityUse.loneHaulerPenalty(nekro(), carrierFrom("301"))).isPositive();
    }

    // The reported bug: with a presence objective out ("Deep Space": units in systems without planets) the cheapest
    // progress was to send the only ship in reach, a carrier with nothing aboard, to stand in an empty system. Nothing
    // is gained by the carrier being there that a ship without capacity could not do, and it leaves the dock.
    @Test
    void doesNotSendALoneEmptyCarrierToStandInAnEmptySystem() {
        board.reveal("deep_space");
        board.test.units(board.home, "space", nekro(), UnitType.Carrier, 1);

        assertThat(best().filter(CapacityUseTest::carriesNothing).filter(CapacityUseTest::movesACarrier))
                .isEmpty();
        assertThat(TacticalPlanner.ranked(board.test.game, nekro()))
                .noneMatch(plan -> carriesNothing(plan) && movesACarrier(plan));
    }

    // The same objective still gets its ship when one without capacity is there to send: positioning to be present is
    // an intentional empty move, it just is not made by the carrier.
    @Test
    void positionsAShipWithoutCapacityForAPresenceObjective() {
        board.reveal("deep_space");
        board.test.units(board.home, "space", nekro(), UnitType.Carrier, 1);
        board.test.units(board.home, "space", nekro(), UnitType.Destroyer, 1);

        TacticalPlan plan = best().orElseThrow();

        assertThat(plan.kind()).isEqualTo(Kind.POSITION);
        assertThat(plan.moves()).containsExactly(new UnitMove(AiTestGame.HOME, "space", UnitType.Destroyer, 1));
    }

    // An empty carrier still moves when what it achieves is worth a point: two other empty systems already hold our
    // ships, and the carrier is the only ship that can reach a third, which completes the objective.
    @Test
    void sendsAnEmptyCarrierWhenItCompletesAnObjective() {
        board.reveal("deep_space");
        String second = board.otherNeighbourOfHome();
        board.test.place("48", second);
        String third = emptySystemBeside(AiTestGame.HOME, board.stagingPosition, second);
        board.test.place("49", third);
        board.test.units(board.staging, "space", nekro(), UnitType.Destroyer, 1);
        board.test.units(board.test.game.getTileByPosition(second), "space", nekro(), UnitType.Destroyer, 1);
        board.test.units(board.home, "space", nekro(), UnitType.Carrier, 1);

        TacticalPlan plan = best().orElseThrow();

        assertThat(plan.kind()).isEqualTo(Kind.POSITION);
        assertThat(plan.target()).isEqualTo(third);
        assertThat(plan.moves()).containsExactly(new UnitMove(AiTestGame.HOME, "space", UnitType.Carrier, 1));
    }

    // Infantry wait at the dock with no ship at home to carry them; the carrier that delivered the last ones is two
    // systems away. Sending it back to the dock is how its capacity gets used next time.
    @Test
    void sendsACarrierBackToTheDockWhereInfantryWait() {
        board.test.units(board.home, HOME_PLANET, nekro(), UnitType.Infantry, 4);
        board.test.nekro.exhaustPlanet(HOME_PLANET);
        Tile away = board.test.place("49", emptySystemBeside(AiTestGame.HOME, board.stagingPosition));
        board.test.units(away, "space", nekro(), UnitType.Carrier, 1);

        TacticalPlan plan = TacticalPlanner.forTarget(board.test.game, nekro(), AiTestGame.HOME)
                .orElseThrow();

        assertThat(plan.kind()).isEqualTo(Kind.PICKUP);
        assertThat(plan.target()).isEqualTo(AiTestGame.HOME);
        assertThat(plan.moves()).isEqualTo(carrierFrom(away.getPosition()));
        assertThat(plan.score()).isPositive();
    }

    // Newly built units sit in the dock's space area. A carrier elsewhere is sent for them.
    @Test
    void collectsUnitsBuiltInTheDockSpaceArea() {
        board.test.units(board.home, "space", nekro(), UnitType.Infantry, 3);
        board.test.nekro.exhaustPlanet(HOME_PLANET);
        Tile away = board.test.place("49", emptySystemBeside(AiTestGame.HOME, board.stagingPosition));
        board.test.units(away, "space", nekro(), UnitType.Carrier, 1);

        TacticalPlan plan = TacticalPlanner.forTarget(board.test.game, nekro(), AiTestGame.HOME)
                .orElseThrow();

        assertThat(plan.kind()).isEqualTo(Kind.PICKUP);
        assertThat(plan.moves()).isEqualTo(carrierFrom(away.getPosition()));
    }

    // Home is exposed (one infantry against a raiding fleet) and a carrier one system away sits beside three infantry
    // on a planet of ours. Reinforcing home with the carrier alone would leave its capacity unused; the defence takes
    // the infantry along and lands them on the home planet.
    @Test
    void aCarrierDefendingHomeBringsGroundForcesAlong() {
        board.raidingFleet(1, 2, 4);
        board.reveal("conquer");
        board.test.units(board.home, HOME_PLANET, nekro(), UnitType.Infantry, 1);
        String awayPosition = board.otherNeighbourOfHome();
        Tile away = board.test.place("26", awayPosition);
        nekro().addPlanet("lodor");
        nekro().exhaustPlanet("lodor");
        board.test.units(away, "lodor", nekro(), UnitType.Infantry, 3);
        board.test.units(away, "space", nekro(), UnitType.Carrier, 1);
        nekro().exhaustPlanet(HOME_PLANET);
        nekro().setTg(0);

        TacticalPlan plan = best().orElseThrow();

        assertThat(plan.kind()).isEqualTo(Kind.DEFEND);
        assertThat(plan.target()).isEqualTo(AiTestGame.HOME);
        assertThat(plan.moves()).contains(new UnitMove(awayPosition, "space", UnitType.Carrier, 1));
        assertThat(plan.moves()).anyMatch(move -> "lodor".equals(move.holder()) && move.type() == UnitType.Infantry);
        assertThat(plan.landings()).containsKey(HOME_PLANET);
    }

    // Nothing waits at the dock: no reason to bring the carrier back.
    @Test
    void doesNotRecallACarrierWhenNothingIsWaiting() {
        board.test.nekro.exhaustPlanet(HOME_PLANET);
        Tile away = board.test.place("49", emptySystemBeside(AiTestGame.HOME, board.stagingPosition));
        board.test.units(away, "space", nekro(), UnitType.Carrier, 1);

        assertThat(TacticalPlanner.forTarget(board.test.game, nekro(), AiTestGame.HOME)
                        .filter(plan -> plan.kind() == Kind.PICKUP))
                .isEmpty();
    }

    // A carrier already at the dock carries what waits there, so a second carrier is not called in for it.
    @Test
    void doesNotRecallACarrierWhenOneIsAlreadyAtTheDock() {
        board.test.units(board.home, HOME_PLANET, nekro(), UnitType.Infantry, 3);
        board.test.units(board.home, "space", nekro(), UnitType.Carrier, 1);
        board.test.nekro.exhaustPlanet(HOME_PLANET);
        Tile away = board.test.place("49", emptySystemBeside(AiTestGame.HOME, board.stagingPosition));
        board.test.units(away, "space", nekro(), UnitType.Carrier, 1);

        assertThat(TacticalPlanner.forTarget(board.test.game, nekro(), AiTestGame.HOME)
                        .filter(plan -> plan.kind() == Kind.PICKUP))
                .isEmpty();
    }

    // A carrier that is itself needed to carry units where it stands does not leave them behind to fetch others.
    @Test
    void doesNotPullACarrierOffUnitsItIsCarrying() {
        board.test.units(board.home, HOME_PLANET, nekro(), UnitType.Infantry, 4);
        board.test.nekro.exhaustPlanet(HOME_PLANET);
        Tile away = board.test.place("49", emptySystemBeside(AiTestGame.HOME, board.stagingPosition));
        board.test.units(away, "space", nekro(), UnitType.Carrier, 1);
        board.test.units(away, "space", nekro(), UnitType.Infantry, 2);

        assertThat(TacticalPlanner.forTarget(board.test.game, nekro(), AiTestGame.HOME)
                        .filter(plan -> plan.kind() == Kind.PICKUP)
                        .filter(plan -> movesOnlyCarriers(plan) && carriesNothing(plan)))
                .isEmpty();
    }
}
