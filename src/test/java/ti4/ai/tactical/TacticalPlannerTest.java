package ti4.ai.tactical;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.eval.BoardView;
import ti4.ai.tactical.TacticalPlan.Kind;
import ti4.ai.tactical.TacticalPlan.UnitMove;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
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

        Optional<TacticalPlan> plan = TacticalPlanner.best(test.game, test.nekro);

        assertThat(plan).isPresent();
        assertThat(plan.get().kind()).isEqualTo(Kind.PRODUCE);
        assertThat(plan.get().target()).isEqualTo(AiTestGame.HOME);
        assertThat(ProductionPlanner.plan(test.game, test.nekro, home).units(UnitType.Carrier))
                .isEqualTo(1);
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
    // faded and the same planet (0.4) is no longer worth a token on its own.
    @Test
    void takesALoneSmallPlanetEarlyButNotLate() {
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
    // dreadnought filler (10 resources at 0.2). Early in the game that is still worth a spare tactic token when
    // nothing else is, but the last token goes to Wellon next door (2.5 + 0.8 tempo - 0.2 distance) instead.
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
    // objective yet, so builtGain does not value them. Four surplus infantry (2 resources at 0.2) plus two pawns
    // (2 resources at 0.5) score 1.4, so the build is still worth a spare token early; discounting the pawns as
    // filler too would score 0.8.
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
