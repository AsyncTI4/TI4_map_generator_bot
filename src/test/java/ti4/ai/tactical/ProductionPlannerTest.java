package ti4.ai.tactical;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.eval.BoardView;
import ti4.ai.scoring.ObjectiveValue;
import ti4.ai.tactical.ProductionPlanner.BuildPlan;
import ti4.game.Tile;
import ti4.helpers.Helper;
import ti4.helpers.Units.UnitType;
import ti4.model.MapPositionMapper;
import ti4.testUtils.BaseTi4Test;

class ProductionPlannerTest extends BaseTi4Test {

    private static final int LEADERSHIP = 1;

    private AiTestGame test;
    private Tile home;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        home = test.nekroHome();
        test.nekro.setFleetCC(5);
    }

    // Mordai II (4 resources) gives the home dock production 6. With resources to spare, every point of it is used:
    // two mechs and the S-tier Alastor, then three fighters for the last three slots.
    @Test
    void usesTheWholeProductionOfTheDock() {
        test.units(home, "space", test.nekro, UnitType.Carrier, 2);
        test.units(home, "mordaiii", test.nekro, UnitType.Infantry, 5);
        test.nekro.setTg(10);

        BuildPlan plan = plan();

        assertThat(plan.units()).isEqualTo(Helper.getProductionValue(test.nekro, test.game, home, false));
        assertThat(plan.units(UnitType.Mech)).isEqualTo(2);
        assertThat(plan.units(UnitType.Flagship)).isEqualTo(1);
        assertThat(plan.units(UnitType.Fighter)).isEqualTo(3);
    }

    // Short of resources, cheap units fill the production instead of a dreadnought that would leave slots empty.
    @Test
    void fillsTheProductionWithCheapUnitsWhenResourcesAreShort() {
        test.units(home, "space", test.nekro, UnitType.Carrier, 2);
        test.units(home, "mordaiii", test.nekro, UnitType.Infantry, 5);

        BuildPlan plan = plan();

        assertThat(plan.units()).isEqualTo(6);
        assertThat(plan.units(UnitType.Dreadnought)).isZero();
        assertThat(plan.totalCost()).isLessThanOrEqualTo(4);
    }

    // Carriers and the infantry they carry come first: with one carrier and an empty home, the second carrier and a
    // carrier load of infantry are bought before anything else.
    @Test
    void buildsTheSecondCarrierAndItsInfantryFirst() {
        test.units(home, "space", test.nekro, UnitType.Carrier, 1);
        test.nekro.setTg(3);

        BuildPlan plan = plan();

        assertThat(plan.units(UnitType.Carrier)).isEqualTo(1);
        assertThat(plan.units(UnitType.Infantry)).isEqualTo(4);
        assertThat(plan.orders().getFirst().type()).isEqualTo(UnitType.Carrier);
    }

    // Fighters ride in carriers, but each carrier keeps room for two infantry, so building fighters never blocks the
    // next expansion. The fleet pool is full, so no ship can be added: the dock's 3 free fighters and the carrier's
    // 2 spare slots take 5 fighters, and the last slot goes to an infantry.
    @Test
    void leavesEachCarrierRoomForInfantry() {
        test.nekro.setFleetCC(1);
        test.units(home, "space", test.nekro, UnitType.Carrier, 1);
        test.units(home, "mordaiii", test.nekro, UnitType.Infantry, 5);
        test.units(home, "mordaiii", test.nekro, UnitType.Mech, 2);
        test.nekro.setTg(20);

        BuildPlan plan = plan();

        int room = BoardView.DOCK_FIGHTER_ALLOWANCE + BoardView.capacity(test.nekro, UnitType.Carrier) - 2;
        assertThat(plan.units(UnitType.Fighter)).isEqualTo(room);
        assertThat(plan.units(UnitType.Infantry)).isEqualTo(1);
    }

    // Engineer a Marvel asks for a flagship or a war sun on the board: the flagship is the cheaper of the two.
    @Test
    void buildsAFlagshipToEngineerAMarvel() {
        reveal("engineer_marvel");
        test.nekro.addOwnedUnitByID("warsun");
        test.nekro.setTg(12);

        BuildPlan plan = plan();

        assertThat(plan.units(UnitType.Flagship)).isEqualTo(1);
        assertThat(plan.units(UnitType.Warsun)).isZero();
        assertThat(plan.scoring()).isTrue();
    }

    // Unveil Flagship needs the flagship itself; a war sun already on the board does not count.
    @Test
    void buildsTheFlagshipForUnveilFlagshipEvenWithAWarSunOut() {
        test.nekro.addOwnedUnitByID("warsun");
        test.units(home, "space", test.nekro, UnitType.Warsun, 1);
        test.nekro.setSecret("uf");
        test.nekro.setTg(8);

        assertThat(plan().units(UnitType.Flagship)).isEqualTo(1);
    }

    // Make an Example of Their World needs bombardment: dreadnoughts are the cheapest ships that have it, and two
    // are kept on the board. One is there already, so one more is built.
    @Test
    void buildsBombardmentShipsToMakeAnExample() {
        test.units(home, "space", test.nekro, UnitType.Dreadnought, 1);
        test.nekro.setSecret("mew");
        test.nekro.setTg(4);

        BuildPlan plan = plan();

        assertThat(plan.units(UnitType.Dreadnought)).isEqualTo(1);
        assertThat(plan.orders().getFirst().type()).isEqualTo(UnitType.Dreadnought);
        assertThat(plan.scoring()).isTrue();
    }

    // Fight with Precision needs anti-fighter barrage, which destroyers have.
    @Test
    void buildsDestroyersToFightWithPrecision() {
        test.nekro.setSecret("fwp");

        BuildPlan plan = plan();

        assertThat(plan.units(UnitType.Destroyer)).isEqualTo(2);
        assertThat(plan.scoring()).isTrue();
    }

    // Two destroyers anywhere on the board used to satisfy Fight with Precision. A dock a destroyer trip from enemy
    // fighters now wants three barrage ships of its own, so two sitting at another system do not count.
    @Test
    void buildsBarrageShipsAtADockFacingFighters() {
        test.nekro.setSecret("fwp");
        test.nekro.setTg(6);
        String first = AiTestGame.neighbourOf(AiTestGame.HOME);
        test.units(test.place("46", first), "space", test.sol, UnitType.Fighter, 2);
        test.units(test.place("26", otherNeighbour(first)), "space", test.nekro, UnitType.Destroyer, 2);

        BuildPlan plan = plan();

        assertThat(plan.units(UnitType.Destroyer)).isEqualTo(3);
        assertThat(plan.scoring()).isTrue();
    }

    @Test
    void countsBarrageShipsAnywhereWhenNoFightersAreNearTheDock() {
        test.nekro.setSecret("fwp");
        test.nekro.setTg(6);
        test.units(
                test.place("26", AiTestGame.neighbourOf(AiTestGame.HOME)), "space", test.nekro, UnitType.Destroyer, 2);

        assertThat(plan().units(UnitType.Destroyer)).isZero();
    }

    // Gather a Mighty Fleet wants five dreadnoughts, and each one built is progress the planner can see.
    @Test
    void valuesDreadnoughtsForGatherAMightyFleet() {
        test.nekro.setSecret("gamf");
        test.nekro.setFleetCC(8);
        test.units(home, "space", test.nekro, UnitType.Dreadnought, 2);
        test.nekro.setTg(12);

        BuildPlan plan = plan();

        assertThat(plan.units(UnitType.Dreadnought)).isEqualTo(3);
        ObjectiveValue value = new ObjectiveValue(test.game, test.nekro);
        assertThat(value.gain(value.before().withBuilt(home.getPosition(), Map.of(UnitType.Dreadnought, 3))))
                .isEqualTo(ObjectiveValue.VICTORY_POINT_VALUE);
    }

    // Any flagship is worth building with resources to spare; its tier sets how many. The S-tier Alastor is built as
    // soon as the rest of the production can still be filled. The D-tier Wrath of Kenara waits for 6 more resources
    // (1.5 per tier below S), and then it is built too.
    @Test
    void needsMoreSpareResourcesForAWeakerFlagship() {
        test.units(home, "space", test.nekro, UnitType.Carrier, 2);
        test.units(home, "mordaiii", test.nekro, UnitType.Infantry, 5);
        test.units(home, "mordaiii", test.nekro, UnitType.Mech, 2);
        test.nekro.setTg(8);
        assertThat(plan().units(UnitType.Flagship)).isEqualTo(1);

        test.nekro.removeOwnedUnitByID("nekro_flagship");
        test.nekro.addOwnedUnitByID("hacan_flagship");
        assertThat(plan().units(UnitType.Flagship)).isZero();

        test.nekro.setTg(16);
        assertThat(plan().units(UnitType.Flagship)).isEqualTo(1);
    }

    // A war sun is a luxury: with the technology it is only built once the resources would still fill the rest of
    // the production.
    @Test
    void buildsAWarSunOnlyWithResourcesToSpare() {
        test.nekro.addOwnedUnitByID("warsun");
        test.units(home, "space", test.nekro, UnitType.Carrier, 2);
        test.units(home, "mordaiii", test.nekro, UnitType.Infantry, 5);
        test.units(home, "mordaiii", test.nekro, UnitType.Mech, 2);
        test.nekro.setTg(10);
        assertThat(plan().units(UnitType.Warsun)).isZero();

        test.nekro.setTg(20);
        assertThat(plan().units(UnitType.Warsun)).isEqualTo(1);
    }

    // Plain destroyers are not worth a production slot, but Destroyer II is: upgraded ones fill out the fleet.
    @Test
    void fillsOutTheFleetWithUpgradedDestroyersOnly() {
        test.nekro.setFleetCC(10);
        test.units(home, "space", test.nekro, UnitType.Carrier, 2);
        test.units(home, "space", test.nekro, UnitType.Dreadnought, 4);
        test.units(home, "space", test.nekro, UnitType.Cruiser, 2);
        test.units(home, "mordaiii", test.nekro, UnitType.Infantry, 5);
        test.units(home, "mordaiii", test.nekro, UnitType.Mech, 2);
        test.nekro.setTg(10);
        assertThat(plan().units(UnitType.Destroyer)).isZero();

        test.nekro.removeOwnedUnitByID("destroyer");
        test.nekro.addOwnedUnitByID("destroyer2");

        assertThat(plan().units(UnitType.Destroyer)).isPositive();
    }

    // Producing runs on a fixed tempo and comes before command tokens: while another player still holds Leadership,
    // production may still spend Mehar Xull (1/3) as well as Mordai II's 4 resources. The Leadership purchase keeps
    // what the build needs instead (see TokensAfterBuildAndResearchTest).
    @Test
    void doesNotHoldInfluenceBackForAPendingLeadershipPurchase() {
        test.game.setPhaseOfGame("action");
        test.place("24", AiTestGame.neighbourOf(AiTestGame.HOME));
        test.nekro.addPlanet("meharxull");
        test.sol.addSC(LEADERSHIP);

        assertThat(ProductionPlanner.spendableResources(test.game, test.nekro)).isEqualTo(5);
    }

    // Nekro's start fills all three fleet tokens. Normally no ship can be added, but when the opening asks for it a
    // second carrier is built past the limit; it is priced net of the destroyer that has to be removed for it.
    @Test
    void buildsACarrierPastTheFleetLimitOnlyWhenAsked() {
        test.nekro.setFleetCC(3);
        test.nekro.setTg(3);
        test.units(home, "space", test.nekro, UnitType.Dreadnought, 1);
        test.units(home, "space", test.nekro, UnitType.Carrier, 1);
        test.units(home, "space", test.nekro, UnitType.Destroyer, 1);
        assertThat(plan().units(UnitType.Carrier)).isZero();

        BuildPlan overSupplied =
                ProductionPlanner.plan(test.game, test.nekro, home, 0, new DefenseOutlook(test.game, test.nekro), 1);

        assertThat(overSupplied.units(UnitType.Carrier)).isEqualTo(1);
        assertThat(overSupplied.units(UnitType.Destroyer) + overSupplied.units(UnitType.Cruiser))
                .isZero();
        assertThat(ProductionPlanner.cheapestShipToScrap(test.nekro, home)).hasValue(1.0);
    }

    // In round 1 the dock's spare production goes to infantry for the expansion before fighters.
    @Test
    void fillsWithInfantryBeforeFightersInRoundOne() {
        test.game.setRound(1);
        test.nekro.setFleetCC(1);
        test.nekro.setTg(3);
        test.units(home, "space", test.nekro, UnitType.Carrier, 1);
        test.units(home, "mordaiii", test.nekro, UnitType.Infantry, 4);

        BuildPlan plan = plan();

        assertThat(plan.units(UnitType.Infantry)).isGreaterThan(plan.units(UnitType.Fighter));
    }

    // Indomitus has a deep space cannon, so a mech on the dock covers the neighbouring systems like a PDS. With a rival
    // close enough to matter, one more is wanted than for a mech without cannon.
    @Test
    void wantsAnExtraCannonMechWhenARivalIsNearby() {
        test.nekro.removeOwnedUnitByID("nekro_mech");
        test.nekro.addOwnedUnitByID("xxcha_mech");
        test.units(home, "space", test.nekro, UnitType.Carrier, 2);
        test.units(home, "mordaiii", test.nekro, UnitType.Infantry, 5);
        test.nekro.setTg(10);
        assertThat(plan().units(UnitType.Mech)).isEqualTo(2);

        test.place("26", AiTestGame.neighbourOf(AiTestGame.HOME));
        test.units(
                test.game.getTileByPosition(AiTestGame.neighbourOf(AiTestGame.HOME)),
                "space",
                test.sol,
                UnitType.Cruiser,
                1);

        assertThat(plan().units(UnitType.Mech)).isEqualTo(3);
    }

    // Four mechs sit on Mordai II and both carriers are away: while Mechanize the Military is held, a carrier is built
    // at
    // home to take the spare mechs out.
    @Test
    void buildsACarrierAtHomeForMechsStuckThereWhileMechanizeIsHeld() {
        Tile away = test.place("22", AiTestGame.neighbourOf(AiTestGame.HOME));
        test.units(away, "space", test.nekro, UnitType.Carrier, 2);
        test.units(home, "mordaiii", test.nekro, UnitType.Mech, 4);
        test.nekro.setTg(10);
        int without = plan().units(UnitType.Carrier);

        test.nekro.setSecret("mtm");

        assertThat(without).isZero();
        assertThat(plan().units(UnitType.Carrier)).isEqualTo(1);
    }

    private static String otherNeighbour(String position) {
        return MapPositionMapper.getAdjacentTilePositions(AiTestGame.HOME).stream()
                .filter(candidate -> !"x".equals(candidate) && !position.equals(candidate))
                .findFirst()
                .orElseThrow();
    }

    private BuildPlan plan() {
        return ProductionPlanner.plan(test.game, test.nekro, home);
    }

    private void reveal(String objective) {
        test.game.getRevealedPublicObjectives().put(objective, 1);
    }

    // Fighters are not limited by the plastic in the box (LRR 23.4): a dock with room for more keeps building them.
    @Test
    void buildsFightersPastTheTenOnTheBoard() {
        test.units(home, "space", test.nekro, UnitType.Carrier, 3);
        test.units(home, "space", test.nekro, UnitType.Fighter, 10);
        test.units(home, "mordaiii", test.nekro, UnitType.Infantry, 5);
        test.nekro.setTg(10);

        assertThat(plan().units(UnitType.Fighter)).isPositive();
    }
}
