package ti4.ai.actioncards;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.ai.scoring.ScoringStakes;
import ti4.ai.strategy.ReadyingValue;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Constants;
import ti4.helpers.Units.UnitType;
import ti4.model.Mapper;
import ti4.testUtils.BaseTi4Test;

class ComponentCardPlansTest extends BaseTi4Test {

    private CardTable table;

    @BeforeEach
    void setUp() {
        table = new CardTable();
    }

    private Optional<Plan> plan(String alias) {
        return AiActionCards.of(alias).orElseThrow().planFor(table.game, table.nekro);
    }

    // ---- Spy ---------------------------------------------------------------------------------

    // Spy takes a random card from a player's hand: the most cards to take from, a contender first.
    @Test
    void spyTakesFromThePlayerWithTheMostCards() {
        Player hacan = table.third("hacan", "yellow");
        table.cards(table.sol, "intercept");
        table.cards(hacan, "rout", "waylay", "bunker");

        assertThat(plan("spy"))
                .hasValueSatisfying(plan -> assertThat(plan.target()).isEqualTo("hacan"));
    }

    @Test
    void spyTargetsAContenderBeforeThePlayerWithTheMostCards() {
        Player hacan = table.third("hacan", "yellow");
        table.cards(table.sol, "intercept");
        table.cards(hacan, "rout", "waylay", "bunker");
        table.points(table.sol, 8);

        Plan plan = plan("spy").orElseThrow();

        assertThat(plan.target()).isEqualTo("sol");
        assertThat(plan.value())
                .isCloseTo(ActionCardPrior.AVERAGE_CARD * TableStanding.CONTENDER + table.stall(), within(1e-9));
    }

    @Test
    void spyNeedsACardToTake() {
        assertThat(plan("spy")).isEmpty();
    }

    // ---- Insubordination ---------------------------------------------------------------------

    @Test
    void insubordinationTargetsTheStrongestPlayerWhoHasNotPassed() {
        Player hacan = table.third("hacan", "yellow");
        table.sol.setTacticalCC(3);
        hacan.setTacticalCC(3);
        table.points(hacan, 4);
        table.points(table.sol, 2);

        assertThat(plan("insub").orElseThrow().target()).isEqualTo("hacan");

        hacan.setPassed(true);
        assertThat(plan("insub").orElseThrow().target()).isEqualTo("sol");
    }

    @Test
    void insubordinationNeedsATacticTokenToTake() {
        table.sol.setTacticalCC(0);

        assertThat(plan("insub")).isEmpty();
    }

    @Test
    void insubordinationTargetsAContenderFirst() {
        Player hacan = table.third("hacan", "yellow");
        table.sol.setTacticalCC(1);
        hacan.setTacticalCC(5);
        table.points(table.sol, 8);
        table.points(hacan, 9);

        assertThat(plan("insub").orElseThrow().target()).isEqualTo("hacan");
    }

    // ---- Uprising ----------------------------------------------------------------------------

    private void solOwns(String tileId, String position, String... planets) {
        table.system(tileId, position);
        for (String planet : planets) table.sol.addPlanet(planet);
    }

    // A planet worth taking is one another player holds ready outside their home system.
    @Test
    void uprisingTargetsAReadiedPlanetOutsideAHomeSystem() {
        table.solHome();
        solOwns("38", "201", "abyz");
        solOwns("19", "202", "wellon");
        table.sol.exhaustPlanet("wellon");

        Plan plan = plan("uprising").orElseThrow();

        assertThat(plan.target()).isEqualTo("sol_abyz");
    }

    @Test
    void uprisingHasNothingToExhaustInAHomeSystemOnly() {
        table.solHome();

        assertThat(plan("uprising")).isEmpty();
    }

    // Erect a Monument takes 8 resources. Sol holds exactly 8 (Jord 4, Abyz 3, Wellon 1), so exhausting a planet that
    // pays them breaks the point, however much richer Meer (4 influence) looks.
    @Test
    void uprisingBreaksAContendersSpendObjectiveBeforeTakingTheRicherPlanet() {
        table.solHome();
        solOwns("38", "201", "abyz");
        solOwns("19", "202", "wellon");
        solOwns("37", "203", "meer");
        table.reveal("monument");
        table.points(table.sol, 8);

        Plan plan = plan("uprising").orElseThrow();

        assertThat(plan.target()).isEqualTo("sol_abyz");
        assertThat(plan.value())
                .isGreaterThan(PlanetTargetCards.uprisingValue(table.game, table.nekro, table.sol, "meer"));
    }

    // Exhausting the planet that pays for Sol's winning objective is worth the whole game.
    @Test
    void uprisingValuesStoppingAWinningScoreAtFullStake() {
        table.solHome();
        solOwns("38", "201", "abyz");
        solOwns("19", "202", "wellon");
        table.reveal("monument");
        table.points(table.sol, 9);

        assertThat(plan("uprising").orElseThrow().value()).isGreaterThanOrEqualTo(ScoringStakes.WIN_VALUE);
    }

    @Test
    void uprisingPrefersTheRicherPlanetWhenNothingBreaks() {
        table.solHome();
        solOwns("38", "201", "abyz");
        solOwns("37", "203", "meer");

        assertThat(plan("uprising").orElseThrow().target()).isEqualTo("sol_abyz");
    }

    private double insubordinationAgainstSol(int tokens) {
        table.sol.setTacticalCC(tokens);
        return plan("insub").orElseThrow().value();
    }

    // Taking a player's last tactic token while they hold 4 unspent resources and a dock costs them a build too.
    @Test
    void insubordinationIsWorthMoreWhenItTakesALastTokenFromAPlayerWhoCouldBuild() {
        Tile home = table.solHomeWithADock();
        assertThat(home).isNotNull();
        double notTheLast = insubordinationAgainstSol(2);

        assertThat(insubordinationAgainstSol(1) - notTheLast)
                .isCloseTo(4 * PlayerTargetCards.LOST_BUILD_PER_RESOURCE, within(1e-9));
    }

    @Test
    void insubordinationSeesNoBuildToLoseWithoutUnspentResources() {
        table.solHomeWithADock();
        table.sol.exhaustPlanet("jord");

        assertThat(insubordinationAgainstSol(1)).isCloseTo(insubordinationAgainstSol(2), within(1e-9));
    }

    @Test
    void insubordinationSeesNoBuildToLoseWithoutADock() {
        table.solHome();

        assertThat(insubordinationAgainstSol(1)).isCloseTo(insubordinationAgainstSol(2), within(1e-9));
    }

    @Test
    void insubordinationPrefersThePlayerItCostsABuildWhenNobodyIsAContender() {
        Player hacan = table.third("hacan", "yellow");
        hacan.setTacticalCC(1);
        table.solHomeWithADock();
        table.sol.setTacticalCC(1);

        assertThat(plan("insub").orElseThrow().target()).isEqualTo("sol");
    }

    // ---- Fighter Conscription ----------------------------------------------------------------

    // A fighter fits in a system with a space dock (3 fighters) or with a ship that has capacity left.
    @Test
    void fighterConscriptionCountsTheSystemsWhereAFighterFits() {
        assertThat(plan("f_conscription").orElseThrow().value()).isCloseTo(0.5 + table.stall(), within(1e-9));

        Tile outpost = table.system("46", AiTestGame.neighbourOf(AiTestGame.HOME));
        table.ships(table.nekro, outpost, UnitType.Carrier, 1);

        assertThat(plan("f_conscription").orElseThrow().value()).isCloseTo(1.0 + table.stall(), within(1e-9));
    }

    @Test
    void fighterConscriptionSkipsASystemWithAnotherPlayersShips() {
        Tile outpost = table.system("46", AiTestGame.neighbourOf(AiTestGame.HOME));
        table.ships(table.nekro, outpost, UnitType.Carrier, 1);
        table.ships(table.sol, outpost, UnitType.Destroyer, 1);

        assertThat(plan("f_conscription").orElseThrow().value()).isCloseTo(0.5 + table.stall(), within(1e-9));
    }

    @Test
    void fighterConscriptionHasNoRoomWithoutADockOrCapacity() {
        table.nekro.getPlanets().clear();
        table.game.getTileMap().clear();

        assertThat(plan("f_conscription")).isEmpty();
    }

    // ---- Ghost Ship --------------------------------------------------------------------------

    @Test
    void ghostShipGoesToAWormholeSystemNobodyElseHasShipsIn() {
        Tile alpha = table.system("39", AiTestGame.neighbourOf(AiTestGame.HOME));

        Plan plan = plan("ghost_ship").orElseThrow();

        assertThat(plan.target()).isEqualTo(alpha.getPosition());
    }

    @Test
    void ghostShipAvoidsASystemWithAnotherPlayersShips() {
        Tile alpha = table.system("39", AiTestGame.neighbourOf(AiTestGame.HOME));
        table.ships(table.sol, alpha, UnitType.Destroyer, 1);

        assertThat(plan("ghost_ship")).isEmpty();
    }

    // A destroyer is worth 1, and 1 more next to the fleet it can join.
    @Test
    void ghostShipIsWorthMoreNextToItsOwnFleet() {
        Tile alpha = table.system("39", AiTestGame.neighbourOf(AiTestGame.HOME));
        double alone = plan("ghost_ship").orElseThrow().value();

        table.ships(table.nekro, table.game.getTileByPosition(AiTestGame.HOME), UnitType.Cruiser, 1);

        assertThat(alone).isCloseTo(1.0 + table.stall(), within(1e-9));
        assertThat(plan("ghost_ship").orElseThrow().value()).isCloseTo(2.0 + table.stall(), within(1e-9));
        assertThat(alpha).isNotNull();
    }

    // Behind the Gate needs a ship in an alpha and a beta wormhole system: the destroyer that completes the pair is
    // worth half the point.
    @Test
    void ghostShipGoesWhereItCompletesAnObjective() {
        table.reveal("btgk");
        table.system("39", "202");
        table.system("40", "203");
        table.ships(table.nekro, table.game.getTileByPosition("202"), UnitType.Cruiser, 1);

        Plan plan = plan("ghost_ship").orElseThrow();

        assertThat(plan.target()).isEqualTo("203");
        assertThat(plan.value()).isGreaterThan(1.0 + 0.5 * 8 + table.stall() - 1e-9);
    }

    @Test
    void ghostShipValuesAWormholeSystemItCouldReachMalliceFrom() {
        table.system("39", "202");
        double withoutNexus = plan("ghost_ship").orElseThrow().value();

        table.system("82a", "310");
        double withNexus = plan("ghost_ship").orElseThrow().value();

        assertThat(withNexus).isCloseTo(withoutNexus + 1.0, within(1e-9));
    }

    // A ship already sitting in a wormhole system connects the seat to Mallice, so another destroyer adds nothing
    // there.
    @Test
    void ghostShipSeesNoGainInMalliceWhenTheSeatAlreadyHasAShipInAWormholeSystem() {
        table.system("39", "202");
        table.system("40", "203");
        table.ships(table.nekro, table.game.getTileByPosition("203"), UnitType.Cruiser, 1);
        double withoutNexus = plan("ghost_ship").orElseThrow().value();

        table.system("82a", "310");

        assertThat(plan("ghost_ship").orElseThrow().value()).isCloseTo(withoutNexus, within(1e-9));
    }

    // The locked nexus (82a) has a gamma wormhole, so the Ghost Ship can land there and flip it. Holding Defy Space and
    // Time makes that destroyer the one that scores, which outweighs the Mallice access of an alpha system.
    @Test
    void ghostShipPicksTheNexusOverAnOrdinaryTargetWhileDefySpaceAndTimeIsHeld() {
        table.system("39", "202");
        table.system("82a", "310");
        String without = plan("ghost_ship").orElseThrow().target();

        table.nekro.setSecret("dfat");

        assertThat(without).isEqualTo("202");
        assertThat(plan("ghost_ship").orElseThrow().target()).isEqualTo("310");
    }

    // ---- Exploration Probe -------------------------------------------------------------------

    private Tile frontier(String position) {
        Tile tile = table.system("41", position);
        tile.addToken(Mapper.getTokenID(Constants.FRONTIER), "space");
        return tile;
    }

    @Test
    void explorationProbeNeedsAFrontierTokenNextToItsShips() {
        String near = AiTestGame.neighbourOf(AiTestGame.HOME);
        Tile token = frontier(near);
        table.ships(table.nekro, table.game.getTileByPosition(AiTestGame.HOME), UnitType.Cruiser, 1);

        Plan plan = plan("probe").orElseThrow();

        assertThat(plan.target()).isEqualTo(token.getPosition());
        assertThat(plan.value()).isCloseTo(ti4.ai.explore.ExploreValues.FRONTIER_CARD + table.stall(), within(1e-9));
    }

    @Test
    void explorationProbeCannotReachADistantFrontierToken() {
        frontier("310");
        table.ships(table.nekro, table.game.getTileByPosition(AiTestGame.HOME), UnitType.Cruiser, 1);

        assertThat(plan("probe")).isEmpty();
    }

    // ---- Archaeological Expedition -----------------------------------------------------------

    @Test
    void archaeologicalExpeditionExploresTheDeckOfAPlanetTypeItHolds() {
        table.system("19", "202");
        table.nekro.addPlanet("wellon");

        Plan plan = plan("arch_expedition").orElseThrow();

        assertThat(plan.target()).isEqualTo("industrial");
        assertThat(plan.value()).isPositive();
    }

    // Home planets have no trait, so a seat with only its home has no deck to explore.
    @Test
    void archaeologicalExpeditionNeedsAPlanetWithATrait() {
        assertThat(plan("arch_expedition")).isEmpty();
    }

    @Test
    void archaeologicalExpeditionNeedsAPlanetOfTheType() {
        table.nekro.getPlanets().clear();

        assertThat(plan("arch_expedition")).isEmpty();
    }

    // ---- Refit Troops ------------------------------------------------------------------------

    @Test
    void refitTroopsSwapsUpToTwoInfantryForMechs() {
        Tile home = table.game.getTileByPosition(AiTestGame.HOME);
        table.test.units(home, "mordaiii", table.nekro, UnitType.Infantry, 3);

        Plan plan = plan("refit").orElseThrow();

        assertThat(List.of(plan.target().split(","))).containsExactly("mordaiii", "mordaiii");
        assertThat(plan.value()).isCloseTo(2 * (2.0 - 0.7) + table.stall(), within(1e-9));
    }

    @Test
    void refitTroopsSwapsOnlyTheInfantryThatIsThere() {
        Tile home = table.game.getTileByPosition(AiTestGame.HOME);
        table.test.units(home, "mordaiii", table.nekro, UnitType.Infantry, 1);

        assertThat(plan("refit").orElseThrow().value()).isCloseTo(1.3 + table.stall(), within(1e-9));
    }

    @Test
    void refitTroopsNeedsInfantry() {
        assertThat(plan("refit")).isEmpty();
    }

    // ---- Economic Initiative -----------------------------------------------------------------

    // Lodor (3 resources, cultural) was spent paying for something; the dock at home can still build with it.
    private void spentCulturalPlanetWithProductionToCome() {
        table.system("26", "202");
        table.nekro.addPlanet("lodor");
        table.nekro.exhaustPlanet("lodor");
        table.nekro.setTacticalCC(3);
        table.nekro.setFleetCC(3);
    }

    @Test
    void economicInitiativeIsWorthWhatReadyingASpentPlanetBuysPlusTheStall() {
        spentCulturalPlanetWithProductionToCome();
        double readying = ReadyingValue.of(table.game, table.nekro, List.of("lodor"));

        assertThat(readying).isPositive();
        assertThat(plan("economic_initiative").orElseThrow().value()).isCloseTo(readying + table.stall(), within(1e-9));
    }

    @Test
    void economicInitiativeIsPlayedWhenASpentCulturalPlanetCanProduceAgain() {
        spentCulturalPlanetWithProductionToCome();
        table.nekro.setActionCard("economic_initiative", 9);
        AiPrompt hand = AiTestGame.prompt("hand", PromptSource.AI_THREAD, AiTestGame.NOW, "ac_play_from_hand_9");

        assertThat(AiTestGame.pressedId(ActionCardRules.playBeforePassing(table.test.context(hand))
                        .orElseThrow()))
                .isEqualTo("ac_play_from_hand_9");
    }

    @Test
    void economicInitiativeIsOnlyAStallWhenEveryCulturalPlanetIsReady() {
        table.system("26", "202");
        table.nekro.addPlanet("lodor");

        assertThat(plan("economic_initiative").orElseThrow().value()).isCloseTo(table.stall(), within(1e-9));
    }

    // A seat that has passed takes no more actions, so readying a planet buys it nothing.
    @Test
    void economicInitiativeReadiesNothingOfValueForASeatThatHasPassedWithoutASpendObjective() {
        spentCulturalPlanetWithProductionToCome();
        table.nekro.setPassed(true);

        assertThat(ReadyingValue.of(table.game, table.nekro, List.of("lodor"))).isZero();
    }

    // Only the cultural planets are readied; a spent hazardous planet changes nothing.
    @Test
    void economicInitiativeIgnoresPlanetsThatAreNotCultural() {
        table.system("38", "202");
        table.nekro.addPlanet("abyz");
        table.nekro.exhaustPlanet("abyz");
        table.nekro.setTacticalCC(3);

        assertThat(plan("economic_initiative").orElseThrow().value()).isCloseTo(table.stall(), within(1e-9));
    }

    // ---- Forward Supply Base -----------------------------------------------------------------

    // The trade good goes to the player with the fewest points, and not to the player who just activated the system
    // when anyone else will do.
    @Test
    void forwardSupplyBaseGivesTheOtherTradeGoodToTheFewestPoints() {
        table.game.setActiveSystem(AiTestGame.HOME);
        Player hacan = table.third("hacan", "yellow");
        table.points(table.sol, 3);
        table.points(hacan, 1);
        table.test.isActive(table.sol, "action");

        Plan plan = plan("fsb").orElseThrow();

        assertThat(plan.target()).isEqualTo("hacan");
        assertThat(plan.value()).isEqualTo(3.0);
    }

    @Test
    void forwardSupplyBaseAvoidsTheActivePlayerWhenItCan() {
        table.game.setActiveSystem(AiTestGame.HOME);
        Player hacan = table.third("hacan", "yellow");
        table.points(table.sol, 3);
        table.points(hacan, 5);
        table.test.isActive(hacan, "action");

        assertThat(plan("fsb").orElseThrow().target()).isEqualTo("sol");
    }

    @Test
    void forwardSupplyBaseGivesTheActivePlayerTheTradeGoodWhenNobodyElseIsLeft() {
        table.game.setActiveSystem(AiTestGame.HOME);
        table.test.isActive(table.sol, "action");

        assertThat(plan("fsb").orElseThrow().target()).isEqualTo("sol");
    }
}
