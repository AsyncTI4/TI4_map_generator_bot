package ti4.ai.tactical;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.secrets.SecretValue;
import ti4.ai.strategy.ResearchPolicy;
import ti4.ai.tactical.TransitDiodesRules.Transfer;
import ti4.game.Planet;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.model.MapPositionMapper;
import ti4.testUtils.BaseTi4Test;

// The ground campaign plans Occupy the Fringe (9 ground forces on a planet without a dock) and Mechanize the Military
// (a mech on each of 4 planets) as one thing: where the units must end up, what to build, which ships carry them and
// how many rounds that takes. Tactical actions are the scarce resource: a system activated this round holds a command
// token and cannot be used as an origin again, so a carrier that delivered to the anchor is back for the next trip two
// rounds later.
class GroundCampaignTest extends BaseTi4Test {

    private static final String OCCUPY = "otf";
    private static final String MECHANIZE = "mtm";

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

    private GroundCampaign campaign() {
        return GroundCampaign.of(test.game, test.nekro);
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

    private Tile bareDock() {
        Tile dock = test.place("29", farFromHome());
        test.nekro.addPlanet("qucenn");
        test.nekro.addPlanet("rarron");
        test.units(dock, "qucenn", test.nekro, UnitType.Spacedock, 1);
        return dock;
    }

    private void adjacentStackWithStockAtHome(int stack, int stock, int carriers) {
        test.nekro.setSecret(OCCUPY);
        test.units(tarmann, "tarmann", test.nekro, UnitType.Infantry, stack);
        test.units(home, "mordaiii", test.nekro, UnitType.Infantry, stock);
        if (carriers > 0) test.units(home, "space", test.nekro, UnitType.Carrier, carriers);
    }

    @Test
    void anAnchorInTheDockSystemNeedsOnlyACarrierInTheSystemAndOneActionPerRound() {
        Tile dock = bareDock();
        test.nekro.setSecret(OCCUPY);
        test.units(dock, "rarron", test.nekro, UnitType.Infantry, 3);
        test.units(dock, "qucenn", test.nekro, UnitType.Infantry, 6);

        assertThat(campaign().anchor().orElseThrow().getName()).isEqualTo("rarron");
        assertThat(campaign().fringeDemand()).isEqualTo(6);
        assertThat(campaign().haulerWanted(dock)).isTrue();
        int withoutShip = campaign().eta(OCCUPY).rounds();

        test.units(dock, "space", test.nekro, UnitType.Carrier, 1);

        assertThat(campaign().haulerWanted(dock)).isFalse();
        assertThat(campaign().eta(OCCUPY).rounds()).isEqualTo(withoutShip - 1);
    }

    @Test
    void aLoneDreadnoughtIsNotAHauler() {
        Tile dock = bareDock();
        test.nekro.setSecret(OCCUPY);
        test.units(dock, "rarron", test.nekro, UnitType.Infantry, 3);
        test.units(dock, "space", test.nekro, UnitType.Dreadnought, 1);

        assertThat(campaign().haulerWanted(dock)).isTrue();
    }

    // Tarmann is next to the home dock: the carrier delivers 4, is stuck there until next round and comes back the
    // round after, so six more take a delivery now and another two rounds later.
    @Test
    void aSingleCarrierMakesOneDeliveryEveryTwoRoundsToAnAdjacentAnchor() {
        adjacentStackWithStockAtHome(3, 9, 1);

        assertThat(campaign().eta(OCCUPY).rounds()).isEqualTo(3);
    }

    @Test
    void aSecondCarrierOrCarrierTwoHaulsTheRestInOneDelivery() {
        adjacentStackWithStockAtHome(3, 9, 1);
        int single = campaign().eta(OCCUPY).rounds();

        assertThat(campaign().eta(OCCUPY, true, false).rounds()).isEqualTo(1);
        test.units(home, "space", test.nekro, UnitType.Carrier, 1);

        assertThat(campaign().eta(OCCUPY).rounds()).isEqualTo(1).isLessThan(single);
    }

    @Test
    void withNoCarrierTheCampaignAsksForOneAndCountsTheRoundItTakes() {
        adjacentStackWithStockAtHome(3, 9, 0);

        assertThat(campaign().haulerWanted(home)).isTrue();
        assertThat(campaign().eta(OCCUPY).rounds()).isGreaterThan(3);
    }

    @Test
    void transitDiodesMoveFourPerRoundSoNoCarrierIsWanted() {
        adjacentStackWithStockAtHome(3, 9, 0);
        test.nekro.addTech("td");

        assertThat(campaign().haulerWanted(home)).isFalse();
        assertThat(campaign().eta(OCCUPY).rounds()).isEqualTo(2);
    }

    @Test
    void productionIsBoundedByWhatTheDockCanBuildEachRound() {
        test.nekro.setSecret(OCCUPY);
        test.units(tarmann, "tarmann", test.nekro, UnitType.Infantry, 1);
        test.units(home, "space", test.nekro, UnitType.Carrier, 2);
        int empty = campaign().eta(OCCUPY).rounds();
        test.units(home, "mordaiii", test.nekro, UnitType.Infantry, 9);

        assertThat(campaign().eta(OCCUPY).rounds()).isLessThan(empty);
    }

    @Test
    void campaignSpendingStartsOnlyWhenTheSlackIsTwoRoundsOrLess() {
        adjacentStackWithStockAtHome(4, 8, 2);
        Planet stack = campaign().anchor().orElseThrow();

        assertThat(campaign().eta(OCCUPY).rounds()).isEqualTo(1);
        assertThat(campaign().fringeActive()).isFalse();
        assertThat(campaign().protects(stack)).isFalse();

        test.points(test.sol, 8);

        assertThat(campaign().fringeActive()).isTrue();
        assertThat(campaign().protects(stack)).isTrue();
    }

    @Test
    void aStackOfFiveIsProtectedEvenWhileTheCampaignIsIdle() {
        adjacentStackWithStockAtHome(5, 8, 2);

        assertThat(campaign().fringeActive()).isFalse();
        assertThat(campaign().protects(campaign().anchor().orElseThrow())).isTrue();
    }

    @Test
    void anInfeasibleCampaignStopsSpendingAndLowersTheKeepValue() {
        adjacentStackWithStockAtHome(3, 9, 0);
        test.points(test.sol, 9);
        GroundCampaign infeasible = campaign();
        double lost = SecretValue.keepValue(test.game, test.nekro, OCCUPY);

        assertThat(infeasible.eta(OCCUPY).feasible()).isFalse();
        assertThat(infeasible.fringeActive()).isFalse();
        assertThat(infeasible.infantryToBuild(home, 0)).isZero();

        test.units(home, "space", test.nekro, UnitType.Carrier, 2);
        test.nekro.addTech("td");

        assertThat(campaign().eta(OCCUPY).feasible()).isTrue();
        assertThat(SecretValue.keepValue(test.game, test.nekro, OCCUPY)).isGreaterThan(lost);
    }

    @Test
    void ferryingThatCompletesTheStackIsAlwaysPulledAndOtherFerryingOnlyWhenActive() {
        adjacentStackWithStockAtHome(4, 8, 2);
        Planet stack = campaign().anchor().orElseThrow();

        assertThat(campaign().ferryPull(stack, 2)).isZero();
        assertThat(campaign().ferryPull(stack, 5)).isPositive();

        test.points(test.sol, 8);

        assertThat(campaign().ferryPull(stack, 2)).isPositive();
    }

    @Test
    void productionBuildsOnlyWhatOneTripCarriesWhileTheCampaignIsIdleAndTheRestWhenActive() {
        test.nekro.setSecret(OCCUPY);
        test.units(tarmann, "tarmann", test.nekro, UnitType.Infantry, 1);
        test.units(home, "space", test.nekro, UnitType.Carrier, 1);
        test.game.setVp(14);

        assertThat(campaign().fringeActive()).isFalse();
        assertThat(campaign().eta(OCCUPY).feasible()).isTrue();
        int idle = campaign().infantryToBuild(home, 0);

        test.game.setVp(10);

        assertThat(campaign().fringeActive()).isTrue();
        assertThat(idle).isEqualTo(4);
        assertThat(campaign().infantryToBuild(home, 0)).isGreaterThan(idle);
    }

    @Test
    void transitDiodesFeedTheAnchorTheCampaignChoseNotTheLargestStack() {
        Tile dock = bareDock();
        test.nekro.setSecret(OCCUPY);
        test.nekro.addTech("td");
        test.units(dock, "rarron", test.nekro, UnitType.Infantry, 3);
        test.units(tarmann, "tarmann", test.nekro, UnitType.Infantry, 5);
        test.units(home, "mordaiii", test.nekro, UnitType.Infantry, 6);

        List<Transfer> plan = TransitDiodesRules.plan(test.game, test.nekro);

        assertThat(plan).isNotEmpty().extracting(Transfer::planet).containsOnly("rarron");
        assertThat(plan).extracting(Transfer::holder).doesNotContain("tarmann");
    }

    @Test
    void transitDiodesLeaveTheGarrisonTheDefenseKeepsAtHome() {
        test.nekro.setSecret(OCCUPY);
        test.nekro.addTech("td");
        test.units(tarmann, "tarmann", test.nekro, UnitType.Infantry, 4);
        test.units(home, "mordaiii", test.nekro, UnitType.Infantry, 1);

        assertThat(TransitDiodesRules.plan(test.game, test.nekro))
                .extracting(Transfer::holder)
                .doesNotContain("tarmann");
    }

    @Test
    void mechanizeTargetsStartWithThePlanetsAlreadyHoldingAMech() {
        test.nekro.setSecret(MECHANIZE);
        Tile abyzFria = test.place("38", other());
        test.nekro.addPlanet("abyz");
        test.nekro.addPlanet("fria");
        test.units(tarmann, "tarmann", test.nekro, UnitType.Mech, 1);

        List<Planet> first = campaign().mtmTargets();

        assertThat(first).hasSize(4);
        assertThat(first.getFirst().getName()).isEqualTo("tarmann");
        assertThat(campaign().bareTargets()).extracting(Planet::getName).doesNotContain("tarmann");
        assertThat(abyzFria).isNotNull();

        test.units(abyzFria, "abyz", test.nekro, UnitType.Mech, 1);

        assertThat(campaign().mtmTargets()).extracting(Planet::getName).contains("tarmann", "abyz");
    }

    private String other() {
        return MapPositionMapper.getAdjacentTilePositions(AiTestGame.HOME).stream()
                .filter(position -> !"x".equals(position)
                        && !neighbour.equals(position)
                        && test.game.getTileByPosition(position) == null)
                .findFirst()
                .orElseThrow();
    }

    @Test
    void withTransitDiodesMechanizeIsBuildMechsThenSpreadThemInOneRound() {
        test.nekro.setSecret(MECHANIZE);
        test.place("38", other());
        test.nekro.addPlanet("abyz");
        test.nekro.addPlanet("fria");
        test.units(home, "mordaiii", test.nekro, UnitType.Mech, 4);
        int withoutDiodes = campaign().eta(MECHANIZE).rounds();

        test.nekro.addTech("td");

        assertThat(withoutDiodes).isGreaterThan(1);
        assertThat(campaign().eta(MECHANIZE).rounds()).isEqualTo(1);
        assertThat(TransitDiodesRules.plan(test.game, test.nekro)).hasSize(3);
    }

    @Test
    void mechanizeBuildsAllFourMechsOnlyOnceTheCampaignIsActive() {
        test.nekro.setSecret(MECHANIZE);
        test.place("38", other());
        test.nekro.addPlanet("abyz");
        test.nekro.addPlanet("fria");
        test.units(home, "mordaiii", test.nekro, UnitType.Mech, 4);

        assertThat(campaign().mechActive()).isTrue();
        assertThat(campaign().mechsWanted(2)).isEqualTo(4);

        test.nekro.addTech("td");

        assertThat(campaign().mechActive()).isFalse();
        assertThat(campaign().mechsWanted(2)).isEqualTo(2);
    }

    @Test
    void slingRelayBuildsTheCarrierTheCampaignNeedsAndNothingSpecialWithoutTheSecret() {
        Tile dock = bareDock();
        test.nekro.addTech("sr");
        test.units(dock, "rarron", test.nekro, UnitType.Infantry, 3);
        Optional<SlingRelayRules.Choice> without = SlingRelayRules.best(test.game, test.nekro);

        test.nekro.setSecret(OCCUPY);
        Optional<SlingRelayRules.Choice> held = SlingRelayRules.best(test.game, test.nekro);

        assertThat(without.orElseThrow().ship().scoring()).isFalse();
        assertThat(held.orElseThrow().ship().type()).isEqualTo(UnitType.Carrier);
        assertThat(held.orElseThrow().ship().scoring()).isTrue();
    }

    @Test
    void carrierTwoIsWorthMoreOnlyWhenItShortensTheCampaignAndCanBeResearchedNow() {
        test.units(tarmann, "tarmann", test.nekro, UnitType.Infantry, 3);
        test.units(home, "mordaiii", test.nekro, UnitType.Infantry, 9);
        test.units(home, "space", test.nekro, UnitType.Carrier, 1);
        double without = ResearchPolicy.value(test.game, test.nekro, "cv2");

        test.nekro.setSecret(OCCUPY);
        assertThat(ResearchPolicy.value(test.game, test.nekro, "cv2")).isEqualTo(without);

        test.nekro.addTech("amd");
        test.nekro.addTech("gd");

        assertThat(ResearchPolicy.value(test.game, test.nekro, "cv2")).isGreaterThan(without + 1);
    }

    @Test
    void carrierTwoIsWorthNothingExtraWhenTheCarriersAlreadyHaulEverythingInOneTrip() {
        test.units(tarmann, "tarmann", test.nekro, UnitType.Infantry, 3);
        test.units(home, "mordaiii", test.nekro, UnitType.Infantry, 9);
        test.units(home, "space", test.nekro, UnitType.Carrier, 2);
        test.nekro.addTech("amd");
        test.nekro.addTech("gd");
        double without = ResearchPolicy.value(test.game, test.nekro, "cv2");

        test.nekro.setSecret(OCCUPY);

        assertThat(ResearchPolicy.value(test.game, test.nekro, "cv2")).isEqualTo(without);
    }

    @Test
    void holdingNeitherSecretLeavesTheCampaignInert() {
        test.units(tarmann, "tarmann", test.nekro, UnitType.Infantry, 6);
        test.units(home, "mordaiii", test.nekro, UnitType.Mech, 3);
        test.units(home, "space", test.nekro, UnitType.Carrier, 1);
        GroundCampaign campaign = campaign();
        Planet tarmannPlanet = test.game.getUnitHolderFromPlanet("tarmann");

        assertThat(campaign.holdsEither()).isFalse();
        assertThat(campaign.anchor()).isEmpty();
        assertThat(campaign.mtmTargets()).isEmpty();
        assertThat(campaign.protects(tarmannPlanet)).isFalse();
        assertThat(campaign.haulerWanted(home)).isFalse();
        assertThat(campaign.infantryToBuild(home, 0)).isZero();
        assertThat(campaign.mechsWanted(2)).isEqualTo(2);
        assertThat(campaign.ferryPull(tarmannPlanet, 4)).isZero();
        assertThat(campaign.fringeDropValue(tarmannPlanet, 2)).isZero();
        assertThat(campaign.mechDropValue(tarmannPlanet)).isZero();
        assertThat(campaign.eta(OCCUPY).done()).isTrue();
        assertThat(campaign.eta(MECHANIZE).done()).isTrue();
        assertThat(TransitDiodesRules.plan(test.game, test.nekro)).isEmpty();
        assertThat(TacticalPlanner.movableGroundForces(test.game, test.nekro, tarmann))
                .isEqualTo(6);
    }
}
