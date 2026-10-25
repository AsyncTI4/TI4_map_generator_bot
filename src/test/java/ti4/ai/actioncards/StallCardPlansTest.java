package ti4.ai.actioncards;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.model.ActionCardModel;
import ti4.model.Mapper;
import ti4.testUtils.BaseTi4Test;

class StallCardPlansTest extends BaseTi4Test {

    private static final String DEFAULT_DECK = "action_cards_te";
    private static final String ACTION_WINDOW = "Action";
    private static final double EXACT = 1e-9;

    // Exchange Program: the agreement path needs a negotiation, and the other path costs the seat a fleet token too.
    private static final Set<String> DEFERRED = Set.of("Exchange Program");

    private CardTable table;

    @BeforeEach
    void setUp() {
        table = new CardTable();
    }

    private Optional<Plan> plan(String alias) {
        return AiActionCards.of(alias).orElseThrow().planFor(table.game, table.nekro);
    }

    private String ccId(Player player) {
        return Mapper.getCCID(player.getColor());
    }

    // ---- the deck ----------------------------------------------------------------------------

    // A card of the Action window is a stall, so none may be left to be discarded as unusable.
    @Test
    void everyActionWindowCardOfTheDeckIsRegisteredOrExplicitlyDeferred() {
        List<String> aliases = Mapper.getDeck(DEFAULT_DECK).getCardIDs();
        Set<String> unhandled = aliases.stream()
                .map(alias -> Mapper.getActionCard(alias))
                .filter(model -> ACTION_WINDOW.equals(model.getWindow()))
                .filter(model -> !AiActionCards.usable(model.getAlias()))
                .map(ActionCardModel::getName)
                .filter(name -> !DEFERRED.contains(name))
                .collect(Collectors.toCollection(java.util.TreeSet::new));

        assertThat(unhandled).isEmpty();
    }

    @Test
    void theDeferredCardsAreReallyInTheDeck() {
        Set<String> names = Mapper.getDeck(DEFAULT_DECK).getCardIDs().stream()
                .map(alias -> Mapper.getActionCard(alias).getName())
                .collect(Collectors.toSet());

        assertThat(names).containsAll(DEFERRED);
    }

    @Test
    void everyActionCardCopyTheDeckHoldsIsUsable() {
        assertThat(AiActionCards.usable("piratecontract1")).isTrue();
        assertThat(AiActionCards.usable("piratecontract4")).isTrue();
    }

    // ---- Unexpected Action -------------------------------------------------------------------

    @Test
    void unexpectedActionNeedsATokenOnTheBoard() {
        assertThat(plan("unexpected")).isEmpty();
    }

    @Test
    void unexpectedActionLiftsOneOfItsOwnTokens() {
        Tile home = table.game.getTileByPosition(AiTestGame.HOME);
        table.test.nekroHome().addCC(ccId(table.nekro));

        Plan plan = plan("unexpected").orElseThrow();

        assertThat(plan.target()).isEqualTo(home.getPosition() + "_nekro");
        assertThat(plan.value()).isGreaterThanOrEqualTo(table.stall());
    }

    @Test
    void unexpectedActionNeverLiftsAnotherPlayersToken() {
        Tile outpost = table.system("40", "302");
        outpost.addCC(ccId(table.sol));

        assertThat(plan("unexpected")).isEmpty();
    }

    // ---- Pirate Contract ---------------------------------------------------------------------

    @Test
    void pirateContractStallsInASystemFarFromItsOwnActivity() {
        table.system("40", "310");

        Plan plan = plan("piratecontract1").orElseThrow();

        assertThat(plan.target()).isEqualTo("310");
        assertThat(plan.value()).isCloseTo(table.stall(), within(EXACT));
    }

    @Test
    void pirateContractNeverBlocksTheSystemsNextToItsOwnPlanetsOrShips() {
        table.system("40", AiTestGame.neighbourOf(AiTestGame.HOME));

        assertThat(plan("piratecontract1")).isEmpty();
    }

    @Test
    void pirateContractNeedsASystemWithoutAnyPlayersShips() {
        Tile outpost = table.system("40", "310");
        table.ships(table.sol, outpost, UnitType.Destroyer, 1);

        assertThat(plan("piratecontract1")).isEmpty();
    }

    @Test
    void anyCopyOfPirateContractIsTheSameCard() {
        table.system("40", "310");

        assertThat(AiActionCards.of("piratecontract3")).isEqualTo(AiActionCards.of("piratecontract1"));
        assertThat(AiActionCards.of("piratecontract3").orElseThrow().planFor(table.game, table.nekro))
                .isPresent();
    }

    // ---- Pirate Fleet and Mercenary Contract -------------------------------------------------

    private void aContenderNextToAnEmptySystem() {
        table.solHome();
        table.points(table.sol, 8);
        table.system("40", "305");
    }

    @Test
    void pirateFleetBlocksAContendersApproachAndPaysForIt() {
        aContenderNextToAnEmptySystem();

        Plan plan = plan("piratefleet").orElseThrow();

        assertThat(plan.target()).isEqualTo("305");
        assertThat(plan.value()).isCloseTo(2.0 * TableStanding.CONTENDER - 3 + table.stall(), within(EXACT));
    }

    @Test
    void pirateFleetIsOnlyForBlockingAContender() {
        table.solHome();
        table.system("40", "305");

        assertThat(plan("piratefleet")).isEmpty();
    }

    @Test
    void pirateFleetNeedsThreeResourcesAfterTheScoringReserve() {
        aContenderNextToAnEmptySystem();
        table.nekro.exhaustPlanet("mordaiii");

        assertThat(plan("piratefleet")).isEmpty();
    }

    private void aContenderHoldsAnEmptyPlanet() {
        table.points(table.sol, 8);
        table.system("19", "203");
        table.sol.addPlanet("wellon");
        table.nekro.setTg(4);
    }

    @Test
    void mercenaryContractPointBlocksAContendersEmptyPlanet() {
        aContenderHoldsAnEmptyPlanet();

        Plan plan = plan("mercenarycontract").orElseThrow();

        assertThat(plan.target()).isEqualTo("wellon");
    }

    @Test
    void mercenaryContractNeedsTwoTradeGoods() {
        aContenderHoldsAnEmptyPlanet();
        table.nekro.setTg(1);

        assertThat(plan("mercenarycontract")).isEmpty();
    }

    @Test
    void mercenaryContractNeverTargetsAPlanetWithUnits() {
        aContenderHoldsAnEmptyPlanet();
        table.test.units(table.game.getTileByPosition("203"), "wellon", table.sol, UnitType.Infantry, 1);

        assertThat(plan("mercenarycontract")).isEmpty();
    }

    @Test
    void mercenaryContractLeavesAPlayerWhoIsNotAContenderAlone() {
        table.system("19", "203");
        table.sol.addPlanet("wellon");
        table.nekro.setTg(4);
        table.game.setVp(20);

        assertThat(plan("mercenarycontract")).isEmpty();
    }

    // ---- Cripple Defenses, Plague, Unstable Planet --------------------------------------------

    private void solHoldsWellonWith(UnitType type, int count) {
        table.solHome();
        table.system("19", "203");
        table.sol.addPlanet("wellon");
        table.test.units(table.game.getTileByPosition("203"), "wellon", table.sol, type, count);
    }

    @Test
    void crippleDefensesDestroysThePdsOfARivalPlanet() {
        solHoldsWellonWith(UnitType.Pds, 2);

        Plan plan = plan("cripple").orElseThrow();

        assertThat(plan.target()).isEqualTo("sol|sol_wellon");
        assertThat(plan.value()).isCloseTo(2.0 + table.stall(), within(EXACT));
    }

    @Test
    void crippleDefensesIsAPureStallOnAPlanetWithoutPds() {
        solHoldsWellonWith(UnitType.Infantry, 1);

        assertThat(plan("cripple").orElseThrow().value()).isCloseTo(table.stall(), within(EXACT));
    }

    @Test
    void crippleDefensesNeverTargetsItsOwnPlanetsOrOnesItsPdsStandOn() {
        table.system("19", "203");
        table.nekro.addPlanet("wellon");
        table.test.units(table.game.getTileByPosition("203"), "wellon", table.nekro, UnitType.Pds, 1);
        assertThat(plan("cripple")).isEmpty();

        table.solHome();
        table.sol.addPlanet("wellon");
        table.nekro.getPlanets().clear();
        assertThat(plan("cripple").orElseThrow().target()).isEqualTo("sol|sol_jord");
    }

    @Test
    void plagueTargetsTheRivalPlanetWithTheMostInfantry() {
        solHoldsWellonWith(UnitType.Infantry, 4);

        Plan plan = plan("plague").orElseThrow();

        assertThat(plan.target()).isEqualTo("sol|sol_wellon");
        assertThat(plan.value()).isCloseTo(0.5 * 4 * 0.5 + table.stall(), within(EXACT));
    }

    @Test
    void plagueRollsForEveryInfantryOnThePlanetSoItNeverTargetsOneItHoldsGroundForcesOn() {
        solHoldsWellonWith(UnitType.Infantry, 4);
        table.test.units(table.game.getTileByPosition("203"), "wellon", table.nekro, UnitType.Infantry, 1);

        assertThat(plan("plague").orElseThrow().target()).isEqualTo("sol|sol_jord");
    }

    @Test
    void unstablePlanetNeedsARivalHazardousPlanet() {
        solHoldsWellonWith(UnitType.Infantry, 2);
        assertThat(plan("unstable")).isEmpty();

        table.system("38", "204");
        table.sol.addPlanet("abyz");
        table.test.units(table.game.getTileByPosition("204"), "abyz", table.sol, UnitType.Infantry, 5);

        Plan plan = plan("unstable").orElseThrow();
        assertThat(plan.target()).isEqualTo("sol|sol_abyz");
        assertThat(plan.value()).isGreaterThan(3 * 0.5 + table.stall() - EXACT);
    }

    @Test
    void unstablePlanetNeverTargetsItsOwnHazardousPlanet() {
        table.system("38", "204");
        table.nekro.addPlanet("abyz");

        assertThat(plan("unstable")).isEmpty();
    }

    // ---- Reactor Meltdown, Tactical Bombardment, Lucky Shot ------------------------------------

    @Test
    void reactorMeltdownDestroysARivalDockOutsideItsHomeSystem() {
        solHoldsWellonWith(UnitType.Spacedock, 1);

        Plan plan = plan("meltdown").orElseThrow();

        assertThat(plan.target()).isEqualTo("sol|sol_203_wellon");
    }

    @Test
    void reactorMeltdownNeverMeltsAHomeSystemDockOrItsOwn() {
        table.solHomeWithADock();
        assertThat(plan("meltdown")).isEmpty();

        table.system("19", "203");
        table.nekro.addPlanet("wellon");
        table.test.units(table.game.getTileByPosition("203"), "wellon", table.nekro, UnitType.Spacedock, 1);
        assertThat(plan("meltdown")).isEmpty();
    }

    @Test
    void tacticalBombardmentExhaustsARivalReadiedPlanetNextToItsDreadnought() {
        table.system("19", "203");
        table.sol.addPlanet("wellon");
        table.ships(table.nekro, table.game.getTileByPosition("203"), UnitType.Dreadnought, 1);

        Plan plan = plan("tactical").orElseThrow();

        assertThat(plan.target()).isEqualTo("203");
        assertThat(plan.value()).isGreaterThan(table.stall());
    }

    @Test
    void tacticalBombardmentNeedsAReadiedRivalPlanetToExhaust() {
        table.system("19", "203");
        table.sol.addPlanet("wellon");
        table.sol.exhaustPlanet("wellon");
        table.ships(table.nekro, table.game.getTileByPosition("203"), UnitType.Dreadnought, 1);

        assertThat(plan("tactical")).isEmpty();
    }

    @Test
    void luckyShotNeedsARivalShipInASystemWithAPlanetOfItsOwn() {
        Tile wellon = table.system("19", "203");
        table.nekro.addPlanet("wellon");
        assertThat(plan("lucky")).isEmpty();

        table.ships(table.sol, wellon, UnitType.Cruiser, 1);

        Plan plan = plan("lucky").orElseThrow();
        assertThat(plan.target()).isEqualTo("203");
        assertThat(plan.value()).isGreaterThan(table.stall());
    }

    @Test
    void luckyShotCannotHitACarrier() {
        Tile wellon = table.system("19", "203");
        table.nekro.addPlanet("wellon");
        table.ships(table.sol, wellon, UnitType.Carrier, 1);

        assertThat(plan("lucky")).isEmpty();
    }

    // ---- Scuttle ---------------------------------------------------------------------------

    @Test
    void scuttleSendsAShipThatDefendsNothingBackForItsCost() {
        Tile outpost = table.system("40", "310");
        table.ships(table.nekro, outpost, UnitType.Cruiser, 1);

        Plan plan = plan("scuttle").orElseThrow();

        assertThat(plan.target()).startsWith("310_0|310_cruiser_0");
        assertThat(plan.value()).isGreaterThan(table.stall());
    }

    @Test
    void scuttleNeverTakesAShipFromASystemWithAPlanetItHolds() {
        Tile wellon = table.system("19", "310");
        table.nekro.addPlanet("wellon");
        table.ships(table.nekro, wellon, UnitType.Cruiser, 1);

        assertThat(plan("scuttle")).isEmpty();
    }

    @Test
    void scuttleNeverTakesAShipFromASystemWithEnemyUnits() {
        Tile outpost = table.system("40", "310");
        table.ships(table.nekro, outpost, UnitType.Cruiser, 1);
        table.ships(table.sol, outpost, UnitType.Destroyer, 1);

        assertThat(plan("scuttle")).isEmpty();
    }

    // ---- Seize Artifact, Repeal Law, Brilliance ----------------------------------------------

    private void solBesideHomeWithAFragment() {
        Tile next = table.system("40", "302");
        table.ships(table.sol, next, UnitType.Destroyer, 1);
        table.sol.addFragment("crf1");
    }

    @Test
    void seizeArtifactTakesAFragmentFromANeighbourWhoHoldsOne() {
        solBesideHomeWithAFragment();

        Plan plan = plan("seize").orElseThrow();

        assertThat(plan.target()).isEqualTo("sol_no|sol_crf1");
        assertThat(plan.value()).isGreaterThan(table.stall());
    }

    @Test
    void seizeArtifactNeedsANeighbourWithAFragment() {
        Tile next = table.system("40", "302");
        table.ships(table.sol, next, UnitType.Destroyer, 1);

        assertThat(plan("seize")).isEmpty();
    }

    @Test
    void repealLawRepealsAPointLawARivalHolds() {
        table.game.addLaw("shard_of_the_throne", "sol");

        Plan plan = plan("repeal").orElseThrow();

        assertThat(plan.target()).isEqualTo(String.valueOf(table.game.getLaws().get("shard_of_the_throne")));
        assertThat(plan.value()).isGreaterThan(table.stall());
    }

    @Test
    void repealLawNeverRepealsALawThatHelpsTheSeat() {
        table.game.addLaw("shard_of_the_throne", "nekro");
        table.game.addLaw("minister_war", "nekro");

        assertThat(plan("repeal")).isEmpty();
    }

    @Test
    void repealLawRepealsALawThatPunishesTheSeat() {
        table.game.addLaw("execution", "nekro");

        assertThat(plan("repeal")).isPresent();
    }

    @Test
    void repealLawNeedsALawInPlay() {
        assertThat(plan("repeal")).isEmpty();
    }

    @Test
    void brillianceReadiesASpentPlanetWithATechnologySpecialty() {
        table.system("27", "203");
        table.nekro.addPlanet("newalbion");
        table.nekro.exhaustPlanet("newalbion");

        Plan plan = plan("brilliance").orElseThrow();

        assertThat(plan.target()).isEqualTo("ready:newalbion");
    }

    @Test
    void brillianceUnlocksTheBreakthroughOfTheWeakestPlayerAsAPureStall() {
        table.sol.addBreakthrough("solbt");

        Plan plan = plan("brilliance").orElseThrow();

        assertThat(plan.target()).isEqualTo("unlock:sol");
    }

    @Test
    void brillianceNeverUnlocksAContendersBreakthrough() {
        table.sol.addBreakthrough("solbt");
        table.points(table.sol, 8);

        assertThat(plan("brilliance")).isEmpty();
    }

    @Test
    void brillianceNeedsSomethingToReadyOrUnlock() {
        assertThat(plan("brilliance")).isEmpty();
    }
}
