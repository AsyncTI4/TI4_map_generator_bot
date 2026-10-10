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

    // The Alastor is an S-tier flagship, so a seat flying it builds it with resources to spare. The Wrath of
    // Kenara is D tier and is only ever built for objectives.
    @Test
    void buildsAGoodFlagshipWithSpareResourcesButNotAWeakOne() {
        test.units(home, "space", test.nekro, UnitType.Carrier, 2);
        test.units(home, "mordaiii", test.nekro, UnitType.Infantry, 5);
        test.units(home, "mordaiii", test.nekro, UnitType.Mech, 2);
        test.nekro.setTg(16);
        assertThat(plan().units(UnitType.Flagship)).isEqualTo(1);

        test.nekro.removeOwnedUnitByID("nekro_flagship");
        test.nekro.addOwnedUnitByID("hacan_flagship");

        assertThat(plan().units(UnitType.Flagship)).isZero();
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
        test.units(home, "space", test.nekro, UnitType.Dreadnought, 3);
        test.units(home, "space", test.nekro, UnitType.Cruiser, 2);
        test.units(home, "mordaiii", test.nekro, UnitType.Infantry, 5);
        test.units(home, "mordaiii", test.nekro, UnitType.Mech, 2);
        test.nekro.setTg(10);
        assertThat(plan().units(UnitType.Destroyer)).isZero();

        test.nekro.removeOwnedUnitByID("destroyer");
        test.nekro.addOwnedUnitByID("destroyer2");

        assertThat(plan().units(UnitType.Destroyer)).isPositive();
    }

    // While another player still holds Leadership, the influence its secondary would spend on command tokens is
    // kept: Mehar Xull (1/3) buys a token, so production may only spend Mordai II's 4 resources.
    @Test
    void keepsInfluenceForAPendingLeadershipPurchase() {
        test.game.setPhaseOfGame("action");
        test.place("24", AiTestGame.neighbourOf(AiTestGame.HOME));
        test.nekro.addPlanet("meharxull");
        test.sol.addSC(LEADERSHIP);

        assertThat(ProductionPlanner.spendableResources(test.game, test.nekro)).isEqualTo(4);

        test.game.setSCPlayed(LEADERSHIP, true);
        test.nekro.addFollowedSC(LEADERSHIP);

        assertThat(ProductionPlanner.spendableResources(test.game, test.nekro)).isEqualTo(5);
    }

    private BuildPlan plan() {
        return ProductionPlanner.plan(test.game, test.nekro, home);
    }

    private void reveal(String objective) {
        test.game.getRevealedPublicObjectives().put(objective, 1);
    }
}
