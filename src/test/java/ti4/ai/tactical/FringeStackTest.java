package ti4.ai.tactical;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.tactical.ProductionPlanner.BuildPlan;
import ti4.ai.tactical.TacticalPlan.Kind;
import ti4.ai.tactical.TacticalPlan.UnitMove;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.model.MapPositionMapper;
import ti4.testUtils.BaseTi4Test;

// Occupy the Fringe (9 ground forces on a planet without one of your space docks): a stack of 3 or more on the anchor
// planet is kept in place, an in-system shift fills the other planet of a dock system, and free infantry drops
// build the stack before it is one drop short.
class FringeStackTest extends BaseTi4Test {

    private static final String OCCUPY = "otf";

    private AiTestGame test;
    private Tile home;
    private String neighbour;
    private Tile tarmann;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.game.setPhaseOfGame("action");
        home = test.nekroHome();
        neighbour = AiTestGame.neighbourOf(AiTestGame.HOME);
        tarmann = test.place("22", neighbour);
        test.nekro.addPlanet("tarmann");
        test.nekro.setTacticalCC(3);
        test.nekro.setTg(6);
    }

    private int movableFromTarmann() {
        return TacticalPlanner.movableGroundForces(test.game, test.nekro, tarmann);
    }

    private String farFromHome() {
        List<String> nearHome = MapPositionMapper.getAdjacentTilePositions(AiTestGame.HOME);
        return MapPositionMapper.getAdjacentTilePositions(neighbour).stream()
                .filter(position -> !"x".equals(position)
                        && !AiTestGame.HOME.equals(position)
                        && !nearHome.contains(position)
                        && test.game.getTileByPosition(position) == null)
                .findFirst()
                .orElseThrow();
    }

    // Tarmann is the best stack without a dock: from 3 ground forces on, none of them may leave it.
    @Test
    void aStackOfThreeOrMoreIsKeptWhileOccupyTheFringeIsHeld() {
        test.units(tarmann, "tarmann", test.nekro, UnitType.Infantry, 6);

        assertThat(movableFromTarmann()).isEqualTo(6);

        test.nekro.setSecret(OCCUPY);

        assertThat(movableFromTarmann()).isZero();
    }

    @Test
    void aSmallerStackStaysMovable() {
        test.nekro.setSecret(OCCUPY);
        test.units(tarmann, "tarmann", test.nekro, UnitType.Infantry, 2);

        assertThat(movableFromTarmann()).isEqualTo(2);
    }

    @Test
    void aStackOfThreeIsNotOfferedToAnExpansion() {
        String target = farFromHome();
        test.place("29", target);
        test.nekro.setSecret(OCCUPY);
        test.units(tarmann, "tarmann", test.nekro, UnitType.Infantry, 3);
        test.units(tarmann, "space", test.nekro, UnitType.Carrier, 1);

        Optional<TacticalPlan> plan = TacticalPlanner.forTarget(test.game, test.nekro, target);

        assertThat(movableFromTarmann()).isZero();
        assertThat(plan.stream().flatMap(found -> found.moves().stream()))
                .noneMatch(move -> move.type() == UnitType.Infantry);
    }

    // Units above the 9 the secret asks for are the surplus.
    @Test
    void onlyTheUnitsAboveTheStackAreSpare() {
        test.nekro.setSecret(OCCUPY);
        test.units(tarmann, "tarmann", test.nekro, UnitType.Infantry, 11);

        assertThat(movableFromTarmann()).isEqualTo(2);
    }

    // With the leader one point from the goal this is the final round: a stack of 6 is still within a drop or two of 9
    // and stays, a stack of 4 is out of reach and its units go back to being spent.
    @Test
    void aStackOfSixStaysProtectedInTheFinalRoundButAStackOfFourIsReleased() {
        test.nekro.setSecret(OCCUPY);
        test.units(tarmann, "tarmann", test.nekro, UnitType.Infantry, 4);
        test.points(test.sol, 9);

        assertThat(movableFromTarmann()).isEqualTo(4);

        test.units(tarmann, "tarmann", test.nekro, UnitType.Infantry, 2);

        assertThat(movableFromTarmann()).isZero();
    }

    @Test
    void aStackOfFourStaysProtectedWhileTheGameStillHasRoundsToRun() {
        test.nekro.setSecret(OCCUPY);
        test.units(tarmann, "tarmann", test.nekro, UnitType.Infantry, 4);
        test.game.setRound(8);

        assertThat(movableFromTarmann()).isZero();
    }

    @Test
    void aPlanetWithADockIsNeverTheStack() {
        test.nekro.setSecret(OCCUPY);
        test.units(home, "mordaiii", test.nekro, UnitType.Infantry, 7);

        assertThat(FringeStack.best(test.game, test.nekro)).isEmpty();
    }

    @Test
    void theStackNextToHomeBeatsAFarOneOfTheSameSize() {
        test.nekro.setSecret(OCCUPY);
        test.units(tarmann, "tarmann", test.nekro, UnitType.Infantry, 5);
        Tile abyzFria = test.place("38", farFromHome());
        test.nekro.addPlanet("abyz");
        test.units(abyzFria, "abyz", test.nekro, UnitType.Infantry, 5);

        assertThat(FringeStack.best(test.game, test.nekro).orElseThrow().getName())
                .isEqualTo("tarmann");
    }

    // A carrier at Tarmann could take its infantry to a free planet next door. While 6 sit there for the secret it
    // takes none; without the secret it does.
    @Test
    void anExpansionLeavesTheStackAlone() {
        String target = farFromHome();
        test.place("29", target);
        test.units(tarmann, "tarmann", test.nekro, UnitType.Infantry, 6);
        test.units(tarmann, "space", test.nekro, UnitType.Carrier, 1);
        Optional<TacticalPlan> without = TacticalPlanner.forTarget(test.game, test.nekro, target);

        test.nekro.setSecret(OCCUPY);
        Optional<TacticalPlan> held = TacticalPlanner.forTarget(test.game, test.nekro, target);

        assertThat(without).isPresent();
        assertThat(without.get().moves()).anyMatch(move -> move.type() == UnitType.Infantry);
        assertThat(held.stream().flatMap(plan -> plan.moves().stream()))
                .noneMatch(move -> move.type() == UnitType.Infantry);
    }

    @Test
    void anExpansionTakesOnlyTheSurplusOverTheStack() {
        String target = farFromHome();
        test.place("29", target);
        test.nekro.setSecret(OCCUPY);
        test.units(tarmann, "tarmann", test.nekro, UnitType.Infantry, 11);
        test.units(tarmann, "space", test.nekro, UnitType.Carrier, 1);

        TacticalPlan plan =
                TacticalPlanner.forTarget(test.game, test.nekro, target).orElseThrow();

        assertThat(plan.moves())
                .filteredOn(move -> move.type() == UnitType.Infantry)
                .extracting(UnitMove::count)
                .allMatch(count -> count <= 2);
    }

    private Tile bareDock() {
        Tile dock = test.place("29", farFromHome());
        test.nekro.addPlanet("qucenn");
        test.nekro.addPlanet("rarron");
        test.units(dock, "qucenn", test.nekro, UnitType.Spacedock, 1);
        return dock;
    }

    private Tile twoPlanetDock() {
        Tile dock = bareDock();
        test.units(dock, "qucenn", test.nekro, UnitType.Infantry, 4);
        test.units(dock, "space", test.nekro, UnitType.Carrier, 1);
        return dock;
    }

    // Qucen'n has the dock and 4 infantry, Rarron is held without one, and a carrier sits in the system: activating
    // it lifts the dock planet's infantry onto Rarron first (one stays to guard the dock), then builds on Qucen'n
    // (LRR 68.3).
    @Test
    void aDockSystemShiftsInfantryToItsOtherPlanetBeforeBuilding() {
        Tile dock = twoPlanetDock();
        test.nekro.setSecret(OCCUPY);

        TacticalPlan plan = TacticalPlanner.forTarget(test.game, test.nekro, dock.getPosition())
                .orElseThrow();

        assertThat(plan.kind()).isEqualTo(Kind.PRODUCE);
        assertThat(plan.moves()).containsExactly(new UnitMove(dock.getPosition(), "qucenn", UnitType.Infantry, 3));
        assertThat(plan.landings()).containsEntry("rarron", 3);
    }

    // The carrier holds 4: with 8 infantry on the dock planet only 4 are lifted.
    @Test
    void theShiftIsLimitedByTheRoomOnTheShipsInTheSystem() {
        Tile dock = twoPlanetDock();
        test.units(dock, "qucenn", test.nekro, UnitType.Infantry, 4);
        test.nekro.setSecret(OCCUPY);

        TacticalPlan plan = TacticalPlanner.forTarget(test.game, test.nekro, dock.getPosition())
                .orElseThrow();

        assertThat(plan.moves()).containsExactly(new UnitMove(dock.getPosition(), "qucenn", UnitType.Infantry, 4));
        assertThat(plan.landings()).containsEntry("rarron", 4);
    }

    @Test
    void noShiftWithoutTheSecret() {
        Tile dock = twoPlanetDock();

        Optional<TacticalPlan> plan = TacticalPlanner.forTarget(test.game, test.nekro, dock.getPosition());

        assertThat(plan.stream().flatMap(found -> found.moves().stream()))
                .noneMatch(move -> move.type() == UnitType.Infantry);
    }

    // Tarmann holds 3 infantry and is the best stack: a free drop of 2 more is worth something even though it does not
    // reach 9.
    @Test
    void aFreeDropOntoTheStackEarnsAShareOfTheSecret() {
        test.units(tarmann, "tarmann", test.nekro, UnitType.Infantry, 3);
        double without = dropValue("tarmann");

        test.nekro.setSecret(OCCUPY);

        assertThat(dropValue("tarmann")).isGreaterThan(without + 1);
    }

    @Test
    void aFreeDropOntoASmallerPlanetEarnsNoShare() {
        test.units(tarmann, "tarmann", test.nekro, UnitType.Infantry, 3);
        double without = dropValue("mordaiii");

        test.nekro.setSecret(OCCUPY);

        assertThat(dropValue("mordaiii")).isEqualTo(without);
    }

    // Rarron shares a system with the Qucen'n dock, Tarmann sits next to the home dock: the dock system wins while its
    // stack is within 2 of the largest, and the largest wins once it is further ahead.
    @Test
    void theAnchorIsTheDocklessPlanetInADockSystemWhileItIsWithinTwoOfTheLargest() {
        Tile dock = twoPlanetDock();
        test.nekro.setSecret(OCCUPY);
        test.units(dock, "rarron", test.nekro, UnitType.Infantry, 3);
        test.units(tarmann, "tarmann", test.nekro, UnitType.Infantry, 5);

        assertThat(FringeStack.best(test.game, test.nekro).orElseThrow().getName())
                .isEqualTo("rarron");

        test.units(tarmann, "tarmann", test.nekro, UnitType.Infantry, 1);

        assertThat(FringeStack.best(test.game, test.nekro).orElseThrow().getName())
                .isEqualTo("tarmann");
    }

    @Test
    void aPlanetNextToADockSystemBeatsAFarOneWithinTwoOfTheLargest() {
        test.nekro.setSecret(OCCUPY);
        test.units(tarmann, "tarmann", test.nekro, UnitType.Infantry, 4);
        Tile abyzFria = test.place("38", farFromHome());
        test.nekro.addPlanet("abyz");
        test.units(abyzFria, "abyz", test.nekro, UnitType.Infantry, 6);

        assertThat(FringeStack.best(test.game, test.nekro).orElseThrow().getName())
                .isEqualTo("tarmann");

        test.units(abyzFria, "abyz", test.nekro, UnitType.Infantry, 1);

        assertThat(FringeStack.best(test.game, test.nekro).orElseThrow().getName())
                .isEqualTo("abyz");
    }

    private BuildPlan buildAt(Tile dock) {
        return ProductionPlanner.plan(test.game, test.nekro, dock);
    }

    // The anchor Rarron holds 3 and the one carrier at the dock is full of fighters, so there is no free room near:
    // all three production slots still go to infantry (6 are missing from 9), to be shifted onto Rarron over the
    // coming rounds, with no second carrier or mech taking the slots.
    @Test
    void productionStockpilesInfantryForAnAnchorInTheDockSystem() {
        Tile dock = bareDock();
        test.units(dock, "space", test.nekro, UnitType.Carrier, 1);
        test.units(dock, "rarron", test.nekro, UnitType.Infantry, 3);
        test.units(dock, "space", test.nekro, UnitType.Fighter, 4);
        test.nekro.setTg(10);
        test.nekro.setFleetCC(5);
        BuildPlan without = buildAt(dock);

        test.nekro.setSecret(OCCUPY);

        BuildPlan held = buildAt(dock);
        assertThat(without.units(UnitType.Infantry)).isLessThan(3);
        assertThat(held.units(UnitType.Infantry)).isEqualTo(3);
        assertThat(held.units(UnitType.Mech)).isZero();
        assertThat(held.units(UnitType.Carrier)).isZero();
    }

    // Carriers are built anyway as general fleet; what the secret adds is a carrier bought as a scoring enabler.
    @Test
    void productionBuildsACarrierForTheSecretWhenNoShipWithCapacityIsNearTheAnchor() {
        Tile dock = bareDock();
        test.units(dock, "rarron", test.nekro, UnitType.Infantry, 3);
        test.nekro.setTg(10);
        test.nekro.setFleetCC(5);

        assertThat(scoringCarriers(buildAt(dock))).isZero();

        test.nekro.setSecret(OCCUPY);

        assertThat(scoringCarriers(buildAt(dock))).isEqualTo(1);
    }

    @Test
    void productionBuildsNoScoringCarrierWhenOneIsAlreadyInTheDockSystem() {
        Tile dock = bareDock();
        test.units(dock, "space", test.nekro, UnitType.Carrier, 1);
        test.units(dock, "rarron", test.nekro, UnitType.Infantry, 3);
        test.nekro.setTg(10);
        test.nekro.setFleetCC(5);
        test.nekro.setSecret(OCCUPY);

        assertThat(scoringCarriers(buildAt(dock))).isZero();
    }

    private static long scoringCarriers(BuildPlan plan) {
        return plan.orders().stream()
                .filter(order -> order.type() == UnitType.Carrier && order.scoring())
                .count();
    }

    // Tarmann holds 5: four infantry from home complete it to 9, which is worth far more than claiming a free planet.
    @Test
    void aGarrisonOfFourOntoAStackOfFiveBeatsAnExpansion() {
        test.nekro.setSecret(OCCUPY);
        test.units(tarmann, "tarmann", test.nekro, UnitType.Infantry, 5);
        String free = AiTestGame.neighbourOf(AiTestGame.HOME).equals(neighbour) ? otherNeighbourOfHome() : neighbour;
        test.place("26", free);
        test.units(home, "mordaiii", test.nekro, UnitType.Infantry, 6);
        test.units(home, "space", test.nekro, UnitType.Carrier, 1);

        TacticalPlan garrison = TacticalPlanner.forTarget(test.game, test.nekro, tarmann.getPosition())
                .orElseThrow();
        Optional<TacticalPlan> expansion = TacticalPlanner.forTarget(test.game, test.nekro, free);

        assertThat(garrison.kind()).isEqualTo(Kind.GARRISON);
        assertThat(expansion.map(TacticalPlan::score).orElse(0.0)).isLessThan(garrison.score());
    }

    private String otherNeighbourOfHome() {
        return MapPositionMapper.getAdjacentTilePositions(AiTestGame.HOME).stream()
                .filter(position -> !"x".equals(position)
                        && !neighbour.equals(position)
                        && test.game.getTileByPosition(position) == null)
                .findFirst()
                .orElseThrow();
    }

    private double dropValue(String planet) {
        return GroundDrops.drops(test.game, test.nekro, Optional.empty()).stream()
                .filter(drop -> drop.planet().equals(planet))
                .mapToDouble(GroundDrops.Drop::infantry)
                .findFirst()
                .orElseThrow();
    }
}
