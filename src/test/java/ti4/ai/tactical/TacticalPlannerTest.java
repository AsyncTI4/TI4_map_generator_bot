package ti4.ai.tactical;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.eval.BoardView;
import ti4.ai.scoring.ObjectiveValue;
import ti4.ai.tactical.TacticalPlan.Kind;
import ti4.ai.tactical.TacticalPlan.UnitMove;
import ti4.game.Tile;
import ti4.helpers.Constants;
import ti4.helpers.Units.UnitType;
import ti4.image.Mapper;
import ti4.image.PositionMapper;
import ti4.testUtils.BaseTi4Test;

class TacticalPlannerTest extends BaseTi4Test {

    private AiTestGame test;
    private Tile home;
    private String neighbour;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        home = test.nekroHome();
        neighbour = AiTestGame.neighbourOf(AiTestGame.HOME);
    }

    // The classic opening: a carrier takes one infantry to an empty neighbouring planet, leaving one infantry
    // and the dreadnought at home.
    @Test
    void expandsToAFreeNeighbouringPlanet() {
        test.units(home, "space", test.nekro, UnitType.Carrier, 1);
        test.units(home, "space", test.nekro, UnitType.Dreadnought, 1);
        test.units(home, "mordaiii", test.nekro, UnitType.Infantry, 2);
        test.place("26", neighbour);

        Optional<TacticalPlan> plan = TacticalPlanner.best(test.game, test.nekro);

        assertThat(plan).isPresent();
        assertThat(plan.get().kind()).isEqualTo(Kind.EXPAND);
        assertThat(plan.get().target()).isEqualTo(neighbour);
        assertThat(plan.get().moves())
                .containsExactlyInAnyOrder(
                        new UnitMove(AiTestGame.HOME, "space", UnitType.Carrier, 1),
                        new UnitMove(AiTestGame.HOME, "mordaiii", UnitType.Infantry, 1));
        assertThat(plan.get().landings()).containsEntry("lodor", 1);
    }

    // It never empties its home system of infantry to expand.
    @Test
    void keepsOneInfantryAtHome() {
        test.units(home, "space", test.nekro, UnitType.Carrier, 1);
        test.units(home, "mordaiii", test.nekro, UnitType.Infantry, 1);
        test.place("26", neighbour);

        Optional<TacticalPlan> plan = TacticalPlanner.forTarget(test.game, test.nekro, neighbour);

        assertThat(plan.filter(found -> found.kind() == Kind.EXPAND)).isEmpty();
    }

    // Quann is two systems away and a carrier moves one. Gravity Drive gives one ship a move of +1 on each tactical
    // action, which is enough for the carrier and its infantry.
    @Test
    void gravityDriveTakesACarrierOneSystemFarther() {
        test.units(home, "space", test.nekro, UnitType.Carrier, 1);
        test.units(home, "mordaiii", test.nekro, UnitType.Infantry, 2);
        test.place("26", neighbour);
        String farther = twoSystemsFromHome();
        test.place("25", farther);
        assertThat(expansionTo(farther)).isEmpty();

        test.nekro.addTech("gd");

        assertThat(expansionTo(farther)).isPresent();
    }

    // Only one ship gets the extra move: of the two carriers at home, one goes to take Sol's undefended New Albion
    // and Starpoint. The dreadnought that cannot reach them stays home as the guard, so the carrier need not.
    @Test
    void gravityDriveSpeedsUpOnlyOneShipOfAnAttack() {
        String farther = twoSystemsFromHome();
        test.place("26", neighbour);
        test.place("27", farther);
        test.sol.addPlanet("newalbion");
        test.sol.addPlanet("starpoint");
        test.units(home, "space", test.nekro, UnitType.Carrier, 2);
        test.units(home, "space", test.nekro, UnitType.Dreadnought, 1);
        test.units(home, "mordaiii", test.nekro, UnitType.Infantry, 3);
        test.nekro.addTech("gd");

        TacticalPlan plan = attackOn(farther).orElseThrow();

        assertThat(plan.moves()).contains(new UnitMove(AiTestGame.HOME, "space", UnitType.Carrier, 1));
        assertThat(plan.moves()).noneMatch(move -> move.type() == UnitType.Dreadnought);
    }

    // A mech is the better garrison: with one at home, the last infantry is free to take Lodor.
    @Test
    void aMechGarrisonFreesTheLastInfantry() {
        test.units(home, "space", test.nekro, UnitType.Carrier, 1);
        test.units(home, "mordaiii", test.nekro, UnitType.Infantry, 1);
        test.units(home, "mordaiii", test.nekro, UnitType.Mech, 1);
        test.place("26", neighbour);

        TacticalPlan plan = expansionTo(neighbour).orElseThrow();

        assertThat(plan.moves())
                .containsExactlyInAnyOrder(
                        new UnitMove(AiTestGame.HOME, "space", UnitType.Carrier, 1),
                        new UnitMove(AiTestGame.HOME, "mordaiii", UnitType.Infantry, 1));
    }

    // A lone carrier is a coin flip against Sol's destroyer, but the fighters at home can ride along and make the
    // attack safe. Two of the carrier's four slots still carry infantry to land on Lodor.
    @Test
    void bringsFightersToWinASpaceCombat() {
        test.units(home, "space", test.nekro, UnitType.Carrier, 1);
        test.units(home, "space", test.nekro, UnitType.Dreadnought, 1);
        test.units(home, "mordaiii", test.nekro, UnitType.Infantry, 4);
        Tile lodor = test.place("26", neighbour);
        test.sol.addPlanet("lodor");
        test.units(lodor, "space", test.sol, UnitType.Destroyer, 1);
        assertThat(attackOn(neighbour)).isEmpty();

        test.units(home, "space", test.nekro, UnitType.Fighter, 2);

        TacticalPlan plan = attackOn(neighbour).orElseThrow();
        assertThat(plan.moves()).contains(new UnitMove(AiTestGame.HOME, "space", UnitType.Fighter, 2));
        assertThat(plan.landings()).containsKey("lodor");
    }

    @Test
    void doesNotExpandIntoAnotherPlayersUnits() {
        test.units(home, "space", test.nekro, UnitType.Carrier, 1);
        test.units(home, "mordaiii", test.nekro, UnitType.Infantry, 2);
        Tile lodor = test.place("26", neighbour);
        test.units(lodor, "space", test.sol, UnitType.Fighter, 1);

        Optional<TacticalPlan> plan = TacticalPlanner.forTarget(test.game, test.nekro, neighbour);

        assertThat(plan.filter(found -> found.kind() == Kind.EXPAND)).isEmpty();
    }

    // With nothing to take, it builds at its home dock: a second carrier first, then infantry.
    @Test
    void producesAtHomeWhenThereIsNothingToTake() {
        test.units(home, "space", test.nekro, UnitType.Carrier, 1);
        test.nekro.setTg(2);

        Optional<TacticalPlan> plan = TacticalPlanner.best(test.game, test.nekro);

        assertThat(plan).isPresent();
        assertThat(plan.get().kind()).isEqualTo(Kind.PRODUCE);
        assertThat(plan.get().target()).isEqualTo(AiTestGame.HOME);
        assertThat(ProductionPlanner.plan(test.game, test.nekro, home).units(UnitType.Carrier))
                .isEqualTo(1);
    }

    // Mordai II's 4 resources buy a carrier and two infantry: three units are not worth activating the system, so
    // the seat keeps its token and its resources for a bigger build.
    @Test
    void doesNotActivateADockToBuildFewerThanFourUnits() {
        test.units(home, "space", test.nekro, UnitType.Carrier, 1);

        assertThat(ProductionPlanner.plan(test.game, test.nekro, home).units()).isEqualTo(3);
        assertThat(TacticalPlanner.best(test.game, test.nekro)).isEmpty();
    }

    // A flagship for Engineer a Marvel scores a point on its own, so that one unit is worth the token.
    @Test
    void buildsALoneFlagshipThatScoresAnObjective() {
        test.game.getRevealedPublicObjectives().put("engineer_marvel", 1);
        test.units(home, "space", test.nekro, UnitType.Carrier, 2);
        test.units(home, "mordaiii", test.nekro, UnitType.Infantry, 5);
        test.nekro.setTg(4);

        assertThat(ProductionPlanner.plan(test.game, test.nekro, home).units()).isEqualTo(1);
        assertThat(productionScoreAtHome()).isGreaterThan(ObjectiveValue.VICTORY_POINT_VALUE);
    }

    @Test
    void doesNothingWithoutTacticTokens() {
        test.units(home, "space", test.nekro, UnitType.Carrier, 1);
        test.units(home, "mordaiii", test.nekro, UnitType.Infantry, 2);
        test.place("26", neighbour);
        test.nekro.setTacticalCC(0);

        assertThat(TacticalPlanner.best(test.game, test.nekro)).isEmpty();
    }

    // A lone infantry cannot be expected to beat three defenders, so the attack gate refuses.
    @Test
    void refusesAttacksWithPoorOdds() {
        test.units(home, "space", test.nekro, UnitType.Carrier, 1);
        test.units(home, "space", test.nekro, UnitType.Dreadnought, 1);
        test.units(home, "mordaiii", test.nekro, UnitType.Infantry, 2);
        Tile lodor = test.place("26", neighbour);
        test.sol.addPlanet("lodor");
        test.units(lodor, "lodor", test.sol, UnitType.Infantry, 3);

        Optional<TacticalPlan> plan = TacticalPlanner.forTarget(test.game, test.nekro, neighbour);

        assertThat(plan.filter(found -> found.kind() == Kind.ATTACK)).isEmpty();
    }

    // Sol holds Lodor with nothing but a War Sun. Ignoring the War Sun would make this a free planet plus a
    // technology copy; a carrier and a dreadnought cannot beat it, so there must be no attack.
    @Test
    void doesNotTreatALoneWarSunAsAnEmptyDefence() {
        test.units(home, "space", test.nekro, UnitType.Carrier, 1);
        test.units(home, "space", test.nekro, UnitType.Dreadnought, 2);
        test.units(home, "mordaiii", test.nekro, UnitType.Infantry, 3);
        Tile lodor = test.place("26", neighbour);
        test.sol.addPlanet("lodor");
        test.sol.addOwnedUnitByID("warsun");
        test.units(lodor, "space", test.sol, UnitType.Warsun, 1);
        assertThat(BoardView.hasEnemyShips(test.game, test.nekro, lodor)).isTrue();

        assertThat(attackOn(neighbour)).isEmpty();

        // A fleet that can beat the War Sun does attack, so it is the War Sun that holds the first fleet back.
        test.units(home, "space", test.nekro, UnitType.Dreadnought, 6);
        test.nekro.setFleetCC(10);
        assertThat(attackOn(neighbour)).isPresent();
    }

    // Attacking from home leaves the least useful ship behind as a guard. The infantry taken along must fit in
    // the ships that actually leave, not in the whole home fleet.
    @Test
    void neverPlansMoreInfantryThanTheDepartingShipsCanCarry() {
        test.units(home, "space", test.nekro, UnitType.Carrier, 2);
        test.units(home, "space", test.nekro, UnitType.Dreadnought, 1);
        test.units(home, "mordaiii", test.nekro, UnitType.Infantry, 12);
        Tile lodor = test.place("26", neighbour);
        test.sol.addPlanet("lodor");
        test.units(lodor, "lodor", test.sol, UnitType.Infantry, 2);

        TacticalPlan plan = attackOn(neighbour).orElseThrow();

        int capacity = plan.moves().stream()
                .filter(move -> BoardView.MOVING_SHIPS.contains(move.type()))
                .mapToInt(move -> move.count() * BoardView.capacity(test.nekro, move.type()))
                .sum();
        int infantry = plan.moves().stream()
                .filter(move -> move.type() == UnitType.Infantry)
                .mapToInt(UnitMove::count)
                .sum();
        assertThat(plan.moves()).noneMatch(move -> move.type() == UnitType.Dreadnought);
        assertThat(infantry).isPositive().isLessThanOrEqualTo(capacity);
        assertThat(plan.landings().values().stream().mapToInt(Integer::intValue).sum())
                .isLessThanOrEqualTo(infantry);
    }

    // Fleet supply counts the AI's ships already waiting in the target system.
    @Test
    void dropsPlansThatWouldExceedFleetSupplyAtTheTarget() {
        test.units(home, "space", test.nekro, UnitType.Carrier, 1);
        test.units(home, "space", test.nekro, UnitType.Dreadnought, 1);
        test.units(home, "mordaiii", test.nekro, UnitType.Infantry, 2);
        Tile lodor = test.place("26", neighbour);
        test.units(lodor, "space", test.nekro, UnitType.Destroyer, 1);

        test.nekro.setFleetCC(2);
        assertThat(TacticalPlanner.forTarget(test.game, test.nekro, neighbour)
                        .filter(found -> found.kind() == Kind.EXPAND))
                .isPresent();

        test.nekro.setFleetCC(1);
        assertThat(TacticalPlanner.forTarget(test.game, test.nekro, neighbour)
                        .filter(found -> found.kind() == Kind.EXPAND))
                .isEmpty();
    }

    // Four infantry against three on Lodor fall short of the 80% bar. X-89 Bacterial Weapon ΩΩ doubles their ground
    // combat hits, which makes the same invasion worth it.
    @Test
    void x89MakesAnInvasionWorthIt() {
        test.units(home, "space", test.nekro, UnitType.Carrier, 1);
        test.units(home, "space", test.nekro, UnitType.Dreadnought, 1);
        test.units(home, "mordaiii", test.nekro, UnitType.Infantry, 5);
        Tile lodor = test.place("26", neighbour);
        test.sol.addPlanet("lodor");
        test.units(lodor, "lodor", test.sol, UnitType.Infantry, 3);

        assertThat(attackOn(neighbour)).isEmpty();

        test.nekro.addTech("x89c4");
        assertThat(attackOn(neighbour).orElseThrow().landings()).containsEntry("lodor", 4);
    }

    // Nekro copies one of the defender's technologies after the first kill. Against Sol's four cruisers, copying
    // Fighter II upgrades its six fighters for the rest of the fight, so the attack scores better when Sol owns
    // Fighter II than when Sol only owns Antimass Deflectors, which would not change the fight.
    @Test
    void countsACombatTechnologyItWouldCopyMidFight() {
        double withAntimass = attackScoreAgainstFourCruisers("amd");
        double withFighterTwo = attackScoreAgainstFourCruisers("ff2");

        assertThat(withFighterTwo).isGreaterThan(withAntimass);
    }

    private double attackScoreAgainstFourCruisers(String solTechnology) {
        AiTestGame fresh = new AiTestGame();
        Tile freshHome = fresh.nekroHome();
        fresh.units(freshHome, "space", fresh.nekro, UnitType.Carrier, 2);
        fresh.units(freshHome, "space", fresh.nekro, UnitType.Dreadnought, 1);
        fresh.units(freshHome, "space", fresh.nekro, UnitType.Fighter, 6);
        fresh.units(freshHome, "mordaiii", fresh.nekro, UnitType.Infantry, 4);
        fresh.nekro.setFleetCC(8);
        Tile lodor = fresh.place("26", neighbour);
        fresh.sol.addPlanet("lodor");
        fresh.units(lodor, "space", fresh.sol, UnitType.Cruiser, 4);
        fresh.sol.addTech(solTechnology);
        return TacticalPlanner.forTarget(fresh.game, fresh.nekro, neighbour)
                .filter(found -> found.kind() == Kind.ATTACK)
                .orElseThrow()
                .score();
    }

    // Dark Energy Tap explores a frontier token when a ship ends a tactical action in its system, so a spare destroyer
    // is worth sending to an empty neighbour holding one. Without the technology there is nothing to gain there.
    @Test
    void sendsAShipToExploreAFrontierWithDarkEnergyTap() {
        test.units(home, "space", test.nekro, UnitType.Destroyer, 1);
        test.units(home, "space", test.nekro, UnitType.Dreadnought, 1);
        Tile empty = test.place("46", neighbour);
        empty.getSpaceUnitHolder().addToken(Mapper.getTokenID(Constants.FRONTIER));

        assertThat(TacticalPlanner.forTarget(test.game, test.nekro, neighbour)).isEmpty();

        test.nekro.addTech("det");
        TacticalPlan plan =
                TacticalPlanner.forTarget(test.game, test.nekro, neighbour).orElseThrow();
        assertThat(plan.kind()).isEqualTo(Kind.POSITION);
        assertThat(plan.moves()).containsExactly(new UnitMove(AiTestGame.HOME, "space", UnitType.Destroyer, 1));
    }

    // Copying a technology after a combat is Nekro's Technological Singularity. A seat without that ability must
    // not count it, so a marginal attack that only the copy made worthwhile is dropped.
    @Test
    void onlyASeatWithTechnologicalSingularityValuesCopyingATechnology() {
        test.units(home, "space", test.nekro, UnitType.Carrier, 1);
        test.units(home, "space", test.nekro, UnitType.Dreadnought, 1);
        test.units(home, "mordaiii", test.nekro, UnitType.Infantry, 5);
        Tile lodor = test.place("26", neighbour);
        test.sol.addPlanet("lodor");
        test.units(lodor, "lodor", test.sol, UnitType.Infantry, 1);

        assertThat(attackOn(neighbour)).isPresent();

        test.nekro.removeAbility("technological_singularity");

        assertThat(attackOn(neighbour)).isEmpty();
    }

    // Xanhact (0/1) is the only free planet left once Cealdri is held. Early in the game any free planet is worth a
    // tactical action, so the expansion clears the bar (0.6 + 0.8 tempo - 0.2 distance); by round 8 the tempo has
    // faded and the same planet (0.4) is no longer worth a token on its own. The explore decks are empty, so that
    // exploring the planet adds nothing to the numbers.
    @Test
    void takesALoneSmallPlanetEarlyButNotLate() {
        test.game.setExploreDeck(new ArrayList<>());
        test.units(home, "space", test.nekro, UnitType.Carrier, 1);
        test.units(home, "space", test.nekro, UnitType.Dreadnought, 1);
        test.units(home, "mordaiii", test.nekro, UnitType.Infantry, 2);
        test.place("73", neighbour);
        test.nekro.addPlanet("cealdri");

        test.game.setRound(2);
        TacticalPlan early = expansionTo(neighbour).orElseThrow();
        assertThat(early.landings()).containsOnlyKeys("xanhact");
        assertThat(early.score()).isGreaterThanOrEqualTo(TacticalPlanner.MIN_SCORE);

        test.game.setRound(8);
        assertThat(expansionTo(neighbour).orElseThrow().score()).isLessThan(TacticalPlanner.MIN_SCORE);
    }

    // With both carriers on the board and a carrier load of infantry already waiting at home, this build is only
    // filler: two mechs, two dreadnoughts and two fighters (13 resources at 0.2). Early in the game that is still
    // worth a spare tactic token when nothing else is, but the last token goes to Wellon next door (2.5 + 0.8 tempo
    // - 0.2 distance) instead.
    @Test
    void prefersAFreePlanetToDreadnoughtFillerWithItsLastToken() {
        test.units(home, "space", test.nekro, UnitType.Carrier, 2);
        test.units(home, "space", test.nekro, UnitType.Dreadnought, 1);
        test.units(home, "mordaiii", test.nekro, UnitType.Infantry, 6);
        test.nekro.setFleetCC(5);
        test.nekro.setTg(10);
        test.nekro.setTacticalCC(1);

        assertThat(TacticalPlanner.best(test.game, test.nekro).map(TacticalPlan::kind))
                .contains(Kind.PRODUCE);

        test.place("19", neighbour);
        Optional<TacticalPlan> plan = TacticalPlanner.best(test.game, test.nekro);

        assertThat(plan.map(TacticalPlan::kind)).contains(Kind.EXPAND);
        assertThat(plan.map(TacticalPlan::target)).contains(neighbour);
    }

    // The same coin flip as above, but a PDS II at home covers Lodor (Deep Space Cannon): its expected hit removes
    // the destroyer before the space combat, so the attack is safe without any fighters.
    @Test
    void countsItsOwnSpaceCannonWhenAttacking() {
        test.units(home, "space", test.nekro, UnitType.Carrier, 1);
        test.units(home, "space", test.nekro, UnitType.Dreadnought, 1);
        test.units(home, "mordaiii", test.nekro, UnitType.Infantry, 4);
        Tile lodor = test.place("26", neighbour);
        test.sol.addPlanet("lodor");
        test.units(lodor, "space", test.sol, UnitType.Destroyer, 1);
        assertThat(attackOn(neighbour)).isEmpty();

        test.nekro.removeOwnedUnitByID("pds");
        test.nekro.addOwnedUnitByID("pds2");
        test.units(home, "mordaiii", test.nekro, UnitType.Pds, 1);

        assertThat(attackOn(neighbour)).isPresent();
    }

    // Destroyer II's barrage (3 dice on 6 each) is expected to shoot down all three of Sol's fighters before the
    // combat, leaving a lone carrier. Plain destroyers (2 dice on 9) only get one, and the fight stays even.
    @Test
    void countsAntiFighterBarrageBeforeTheSpaceCombat() {
        test.units(home, "space", test.nekro, UnitType.Carrier, 1);
        test.units(home, "space", test.nekro, UnitType.Destroyer, 3);
        test.units(home, "mordaiii", test.nekro, UnitType.Infantry, 3);
        Tile lodor = test.place("26", neighbour);
        test.sol.addPlanet("lodor");
        test.units(lodor, "space", test.sol, UnitType.Carrier, 1);
        test.units(lodor, "space", test.sol, UnitType.Fighter, 3);
        test.nekro.setFleetCC(5);
        assertThat(attackOn(neighbour)).isEmpty();

        test.nekro.removeOwnedUnitByID("destroyer");
        test.nekro.addOwnedUnitByID("destroyer2");

        assertThat(attackOn(neighbour)).isPresent();
    }

    // A cruiser stays home as the guard, so a carrier and two cruisers attack Sol's two cruisers: too even a fight.
    // With Assault Cannon those three ships make Sol destroy a cruiser before the combat, and the attack is on.
    @Test
    void countsAssaultCannonBeforeTheSpaceCombat() {
        test.units(home, "space", test.nekro, UnitType.Carrier, 1);
        test.units(home, "space", test.nekro, UnitType.Cruiser, 3);
        test.units(home, "mordaiii", test.nekro, UnitType.Infantry, 3);
        Tile lodor = test.place("26", neighbour);
        test.sol.addPlanet("lodor");
        test.units(lodor, "space", test.sol, UnitType.Cruiser, 2);
        test.nekro.setFleetCC(5);
        assertThat(attackOn(neighbour)).isEmpty();

        test.nekro.addTech("asc");

        assertThat(attackOn(neighbour)).isPresent();
    }

    // A dreadnought stays home as the guard, so a carrier and a dreadnought attack Sol's two cruisers: not safe enough.
    // Non-Euclidean Shielding lets the dreadnought's sustain cancel two hits, and the attack is on.
    @Test
    void countsNonEuclideanShieldingInTheOdds() {
        test.units(home, "space", test.nekro, UnitType.Carrier, 1);
        test.units(home, "space", test.nekro, UnitType.Dreadnought, 2);
        test.units(home, "mordaiii", test.nekro, UnitType.Infantry, 3);
        Tile lodor = test.place("26", neighbour);
        test.sol.addPlanet("lodor");
        test.units(lodor, "space", test.sol, UnitType.Cruiser, 2);
        test.nekro.setFleetCC(5);
        assertThat(attackOn(neighbour)).isEmpty();

        test.nekro.addTech("nes");

        assertThat(attackOn(neighbour)).isPresent();
    }

    // A damaged dreadnought can no longer sustain damage, but it still fights: two of the three dreadnoughts are
    // damaged, one stays home as the guard, and the other two join the attack instead of waiting for repairs.
    @Test
    void bringsDamagedShipsIntoAnAttack() {
        test.units(home, "space", test.nekro, UnitType.Carrier, 1);
        test.units(home, "space", test.nekro, UnitType.Dreadnought, 3);
        home.addUnitDamage("space", ti4.helpers.Units.getUnitKey(UnitType.Dreadnought, test.nekro.getColor()), 2);
        test.units(home, "mordaiii", test.nekro, UnitType.Infantry, 3);
        test.nekro.setFleetCC(5);
        Tile lodor = test.place("26", neighbour);
        test.sol.addPlanet("lodor");
        test.units(lodor, "space", test.sol, UnitType.Destroyer, 1);

        TacticalPlan plan = attackOn(neighbour).orElseThrow();

        assertThat(plan.moves()).contains(new UnitMove(AiTestGame.HOME, "space", UnitType.Dreadnought, 2));
    }

    // Infantry are worth building while the dock is short of a carrier load; beyond that they are filler, so the same
    // four infantry score less once the home system already holds spares.
    @Test
    void valuesInfantryOnlyWhileTheDockIsShortOfACarrierLoad() {
        test.units(home, "space", test.nekro, UnitType.Carrier, 2);
        test.units(home, "space", test.nekro, UnitType.Dreadnought, 1);
        test.units(home, "mordaiii", test.nekro, UnitType.Infantry, 1);
        double shortOfInfantry = productionScoreAtHome();

        test.units(home, "mordaiii", test.nekro, UnitType.Infantry, 5);
        double stocked = productionScoreAtHome();

        assertThat(shortOfInfantry).isGreaterThan(stocked);
    }

    // Destroyers built as pawns for a revealed presence objective are not filler: building them at home moves no
    // objective yet, so builtGain does not value them. Four fighters (2 resources at 0.2) plus two pawns (2
    // resources at 0.5) score 1.4, so the build is still worth a spare token early; discounting the pawns as filler
    // too would score 0.8.
    @Test
    void keepsTheValueOfDestroyerPawnsForAPresenceObjectiveEarly() {
        test.game.setPhaseOfGame("action");
        test.game.getRevealedPublicObjectives().put("deep_space", 1);
        test.units(home, "space", test.nekro, UnitType.Carrier, 2);
        test.units(home, "space", test.nekro, UnitType.Dreadnought, 3);
        test.units(home, "mordaiii", test.nekro, UnitType.Infantry, 6);
        test.nekro.setFleetCC(7);
        assertThat(ProductionPlanner.plan(test.game, test.nekro, home).units(UnitType.Destroyer))
                .isEqualTo(2);

        assertThat(productionScoreAtHome()).isGreaterThanOrEqualTo(TacticalPlanner.MIN_SCORE);
    }

    // The last carrier to leave home fills up with spare infantry beyond the one it lands. The rest stay aboard, so
    // next round it can take more planets from wherever it ends up. The home garrison stays.
    @Test
    void fillsTheLastCarrierLeavingHomeWithSpareInfantry() {
        test.units(home, "space", test.nekro, UnitType.Carrier, 1);
        test.units(home, "space", test.nekro, UnitType.Dreadnought, 1);
        test.units(home, "mordaiii", test.nekro, UnitType.Infantry, 6);
        test.place("26", neighbour);

        TacticalPlan plan = expansionTo(neighbour).orElseThrow();

        assertThat(plan.moves())
                .containsExactlyInAnyOrder(
                        new UnitMove(AiTestGame.HOME, "space", UnitType.Carrier, 1),
                        new UnitMove(AiTestGame.HOME, "mordaiii", UnitType.Infantry, 4));
        assertThat(plan.landings()).containsOnlyKeys("lodor").containsEntry("lodor", 1);
    }

    // A second carrier still at home may need those infantry for its own expansion this round, so the first one takes
    // only what it lands.
    @Test
    void leavesSpareInfantryHomeWhileAnotherCarrierIsThere() {
        test.units(home, "space", test.nekro, UnitType.Carrier, 2);
        test.units(home, "space", test.nekro, UnitType.Dreadnought, 1);
        test.units(home, "mordaiii", test.nekro, UnitType.Infantry, 6);
        test.place("26", neighbour);

        TacticalPlan plan = expansionTo(neighbour).orElseThrow();

        assertThat(plan.moves())
                .containsExactlyInAnyOrder(
                        new UnitMove(AiTestGame.HOME, "space", UnitType.Carrier, 1),
                        new UnitMove(AiTestGame.HOME, "mordaiii", UnitType.Infantry, 1));
    }

    // Mecatol Rex is worth an Imperial point every round it is held, so its last infantry stays: an empty Mecatol
    // is the easiest planet on the board for a neighbour to take.
    @Test
    void keepsAnInfantryOnMecatolRex() {
        Tile rex = test.place("18", "000");
        test.nekro.addPlanet("mr");
        test.units(rex, "space", test.nekro, UnitType.Carrier, 1);
        test.units(rex, "mr", test.nekro, UnitType.Infantry, 1);
        test.place("26", "101");

        assertThat(expansionTo("101")).isEmpty();

        // A second infantry on Mecatol is free to leave and take Lodor.
        test.units(rex, "mr", test.nekro, UnitType.Infantry, 1);
        assertThat(expansionTo("101")).isPresent();
    }

    private String twoSystemsFromHome() {
        List<String> nearHome = PositionMapper.getAdjacentTilePositions(AiTestGame.HOME);
        return PositionMapper.getAdjacentTilePositions(neighbour).stream()
                .filter(position ->
                        !"x".equals(position) && !AiTestGame.HOME.equals(position) && !nearHome.contains(position))
                .findFirst()
                .orElseThrow();
    }

    // Taking a planet nobody holds explores it, so the planet is worth the average of what is left in its deck on
    // top of its own value. A Dyson Sphere alone (2 resources and 1 influence, 2.6) raises the plan by exactly that.
    @Test
    void valuesExploringAPlanetNobodyHolds() {
        test.units(home, "space", test.nekro, UnitType.Carrier, 1);
        test.units(home, "mordaiii", test.nekro, UnitType.Infantry, 2);
        test.place("26", neighbour);

        test.game.setExploreDeck(new ArrayList<>());
        double unexplored = expansionTo(neighbour).orElseThrow().score();
        test.game.setExploreDeck(new ArrayList<>(List.of("ds")));
        double explored = expansionTo(neighbour).orElseThrow().score();

        assertThat(explored - unexplored).isCloseTo(2.6, org.assertj.core.data.Offset.offset(1e-9));
    }

    // A planet that someone holds is not explored when it is invaded, so the same deck changes nothing.
    @Test
    void doesNotValueExploringAPlanetAnotherPlayerHolds() {
        test.units(home, "space", test.nekro, UnitType.Carrier, 1);
        test.units(home, "space", test.nekro, UnitType.Dreadnought, 1);
        test.units(home, "mordaiii", test.nekro, UnitType.Infantry, 5);
        Tile lodor = test.place("26", neighbour);
        test.sol.addPlanet("lodor");
        test.units(lodor, "lodor", test.sol, UnitType.Infantry, 1);

        test.game.setExploreDeck(new ArrayList<>());
        double without = attackOn(neighbour).orElseThrow().score();
        test.game.setExploreDeck(new ArrayList<>(List.of("ds")));
        double with = attackOn(neighbour).orElseThrow().score();

        assertThat(with).isEqualTo(without);
    }

    // The same attack on a planet nobody holds (but that has defenders) does explore it once it is taken.
    @Test
    void valuesExploringAPlanetTakenFromGroundForcesThatDoNotHoldIt() {
        test.units(home, "space", test.nekro, UnitType.Carrier, 1);
        test.units(home, "space", test.nekro, UnitType.Dreadnought, 1);
        test.units(home, "mordaiii", test.nekro, UnitType.Infantry, 5);
        Tile lodor = test.place("26", neighbour);
        test.units(lodor, "lodor", test.sol, UnitType.Infantry, 1);

        test.game.setExploreDeck(new ArrayList<>());
        double without = attackOn(neighbour).orElseThrow().score();
        test.game.setExploreDeck(new ArrayList<>(List.of("ds")));
        double with = attackOn(neighbour).orElseThrow().score();

        assertThat(with).isGreaterThan(without);
    }

    private Optional<TacticalPlan> attackOn(String position) {
        return TacticalPlanner.forTarget(test.game, test.nekro, position).filter(found -> found.kind() == Kind.ATTACK);
    }

    private Optional<TacticalPlan> expansionTo(String position) {
        return TacticalPlanner.forTarget(test.game, test.nekro, position).filter(found -> found.kind() == Kind.EXPAND);
    }

    private double productionScoreAtHome() {
        return TacticalPlanner.forTarget(test.game, test.nekro, AiTestGame.HOME)
                .filter(found -> found.kind() == Kind.PRODUCE)
                .orElseThrow()
                .score();
    }
}
