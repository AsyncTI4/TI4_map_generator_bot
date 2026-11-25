package ti4.ai.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToDoubleFunction;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelperTacticalAction;
import ti4.helpers.Constants;
import ti4.helpers.Units.UnitType;
import ti4.service.map.FractureService;
import ti4.testUtils.BaseTi4Test;

class MoveBudgetTest extends BaseTi4Test {

    private static final ToDoubleFunction<UnitType> COST = type -> switch (type) {
        case Dreadnought -> 4;
        case Carrier, Cruiser -> 3;
        case Destroyer -> 1;
        case Infantry, Fighter -> 0.5;
        case Mech -> 2;
        default -> 1;
    };

    private static final int WARFARE_INITIATIVE = 6;

    private final MoveLine line = new MoveLine(4);
    private final Game game = line.test.game;
    private final Player seat = line.test.nekro;

    private void unlock(String breakthrough) {
        seat.addBreakthrough(breakthrough);
        seat.setBreakthroughUnlocked(breakthrough, true);
    }

    private Departure from(int step, UnitType type, int count) {
        return new Departure(line.positions.get(step), type, count);
    }

    private void activate(Tile target) {
        line.test.isActive(seat, "action");
        game.setActiveSystem(target.getPosition());
        game.setStoredValue("currentActionSummary" + seat.getFaction(), " Activated " + target.getPosition());
    }

    // The nebula caps the cruiser at 1 first, then Gravity Drive adds its +1: two systems, never three.
    @Test
    void aNebulaOriginWithGravityDriveMovesTwo() {
        line.swap(0, "42");
        line.ships(0, UnitType.Cruiser, 1);
        assertThat(MoveBudget.canJoin(game, seat, line.at(0), line.at(2), UnitType.Cruiser))
                .isFalse();

        seat.addTech("gd");
        assertThat(MoveBudget.canJoin(game, seat, line.at(0), line.at(2), UnitType.Cruiser))
                .isTrue();
        assertThat(MoveBudget.canJoin(game, seat, line.at(0), line.at(3), UnitType.Cruiser))
                .isFalse();
    }

    // Gravity Drive and the Ionian Fuel Refinery each give +1 to one ship; nothing stops both going on the same one.
    @Test
    void gravityDriveAndTempestaStackOnOneShip() {
        line.ships(0, UnitType.Carrier, 1);
        List<Departure> carrier = List.of(from(0, UnitType.Carrier, 1));
        seat.addTech("gd");
        seat.addPlanet("tempesta");

        assertThat(MoveBudget.oneShip(game, seat, line.at(3))).isEqualTo(2);
        assertThat(MoveBudget.boostsNeeded(game, seat, line.at(3), carrier)).isEqualTo(2);
        assertThat(MoveBudget.feasible(game, seat, line.at(3), carrier)).isTrue();
        assertThat(MoveBudget.feasible(game, seat, line.at(4), carrier)).isFalse();
    }

    // A Tempesta already spent in this activation still counts when the plan is checked again mid-movement.
    @Test
    void tempestaSpentThisActivationStillCounts() {
        seat.addPlanet("tempesta");
        seat.exhaustPlanetAbility("tempesta");
        assertThat(MoveBudget.oneShip(game, seat, line.at(2))).isZero();

        activate(line.at(2));
        game.setStoredValue("tempestaUsed", seat.getFaction());
        assertThat(MoveBudget.oneShip(game, seat, line.at(2))).isEqualTo(1);
    }

    // Tempesta spent in an earlier activation this round is exhausted: the engine clears tempestaUsed when the
    // next system is activated, so a later activation (next turn or a second action) gets no phantom +1.
    @Test
    void tempestaSpentInAnEarlierActivationDoesNotCount() {
        seat.addPlanet("tempesta");
        seat.exhaustPlanetAbility("tempesta");
        activate(line.at(1));
        game.setStoredValue("tempestaUsed", seat.getFaction());

        ButtonHelperTacticalAction.clearActivationBoosts(game);
        activate(line.at(2));
        assertThat(MoveBudget.oneShip(game, seat, line.at(2))).isZero();
        assertThat(MoveBudget.tempestaThisAction(game, seat)).isFalse();
    }

    // Another seat's turn: the spender is not active, so its spent Tempesta never counts for threat estimates.
    @Test
    void tempestaSpentDoesNotCountWhenTheSpenderIsNotActive() {
        seat.addPlanet("tempesta");
        seat.exhaustPlanetAbility("tempesta");
        game.setStoredValue("tempestaUsed", seat.getFaction());
        game.setStoredValue(
                "currentActionSummary" + seat.getFaction(),
                " Activated " + line.at(2).getPosition());
        game.setActivePlayerID("someone-else");
        assertThat(MoveBudget.tempestaThisAction(game, seat)).isFalse();
    }

    // Gravity Drive is one +1 per tactical action: two carriers from two systems cannot both use it.
    @Test
    void gravityDriveBoostsOneShipAcrossTwoOrigins() {
        seat.addTech("gd");
        line.ships(0, UnitType.Carrier, 1);
        line.ships(4, UnitType.Carrier, 1);
        Tile target = line.at(2);
        List<Departure> both = List.of(from(0, UnitType.Carrier, 1), from(4, UnitType.Carrier, 1));

        assertThat(MoveBudget.boostsNeeded(game, seat, target, both)).isEqualTo(2);
        assertThat(MoveBudget.feasible(game, seat, target, both)).isFalse();
        assertThat(MoveBudget.feasible(game, seat, target, List.of(from(4, UnitType.Carrier, 1))))
                .isTrue();

        Map<String, Map<UnitType, Integer>> available = Map.of(
                line.positions.get(0), Map.of(UnitType.Carrier, 1),
                line.positions.get(4), Map.of(UnitType.Carrier, 1));
        assertThat(MoveBudget.allocate(game, seat, target, available, COST, 10)).hasSize(1);
    }

    // Beyond even the boosted move there is no legal path, and the whole set is infeasible.
    @Test
    void aCarrierBeyondItsBoostedMoveHasNoPath() {
        seat.addTech("gd");
        line.ships(0, UnitType.Carrier, 1);
        line.ships(4, UnitType.Carrier, 1);
        List<Departure> both = List.of(from(0, UnitType.Carrier, 1), from(4, UnitType.Carrier, 1));

        assertThat(MoveBudget.boostsNeeded(game, seat, line.at(3), both)).isEqualTo(Integer.MAX_VALUE);
        assertThat(MoveBudget.boostsNeeded(game, seat, line.at(3), List.of(from(4, UnitType.Carrier, 1))))
                .isZero();
    }

    // When boosts are scarce the boost goes to the carrier whose infantry make it worth more.
    @Test
    void allocateSpendsTheBoostOnTheMostValuableShip() {
        seat.addTech("gd");
        line.ships(0, UnitType.Carrier, 1);
        line.ships(4, UnitType.Carrier, 1);
        line.test.units(line.at(4), "space", seat, UnitType.Infantry, 4);
        Tile target = line.at(2);
        Map<String, Map<UnitType, Integer>> available = Map.of(
                line.positions.get(0), Map.of(UnitType.Carrier, 1),
                line.positions.get(4), Map.of(UnitType.Carrier, 1));

        assertThat(MoveBudget.allocate(game, seat, target, available, COST, 10))
                .containsOnlyKeys(line.positions.get(4));

        seat.addPlanet("tempesta");
        Map<String, Map<UnitType, Integer>> both = MoveBudget.allocate(game, seat, target, available, COST, 10);
        assertThat(both).containsOnlyKeys(line.positions.get(0), line.positions.get(4));
    }

    // Styx is a legendary planet (Thunder's Edge), so Imperator gives one ship +1 into its system.
    @Test
    void imperatorCountsIntoStyx() {
        FractureService.spawnFracture(null, game);
        Tile styx = game.getTileByPosition("frac4");
        assertThat(styx.hasLegendary()).isTrue();

        unlock("winnubt");
        assertThat(MoveBudget.oneShip(game, seat, styx)).isEqualTo(1);
        assertThat(MoveBudget.oneShip(game, seat, line.at(1))).isZero();
    }

    // Gravleash: the dreadnought keeps pace with the cruiser that leaves the same system, even beyond its own move.
    @Test
    void aLeashedDreadnoughtGoesBeyondItsOwnRange() {
        unlock("letnevbt");
        line.ships(0, UnitType.Dreadnought, 2);
        line.ships(0, UnitType.Cruiser, 1);
        Tile target = line.at(2);
        List<Departure> together = List.of(from(0, UnitType.Dreadnought, 2), from(0, UnitType.Cruiser, 1));

        assertThat(MoveBudget.boostsNeeded(game, seat, target, together)).isZero();
        assertThat(MoveBudget.boostsNeeded(game, seat, target, List.of(from(0, UnitType.Dreadnought, 2))))
                .isEqualTo(1);
        assertThat(MoveBudget.canJoin(game, seat, line.at(0), target, UnitType.Dreadnought))
                .isTrue();
        assertThat(MoveBudget.groupSpeed(game, seat, line.at(0), target, together, 1))
                .isEqualTo(3);
    }

    // With Gravity Drive on the cruiser (Letnev ruling I) the whole leashed stack reaches three systems away.
    @Test
    void gravityDriveOnThePaceSetterLiftsTheWholeStack() {
        unlock("letnevbt");
        seat.addTech("gd");
        line.ships(0, UnitType.Dreadnought, 2);
        line.ships(0, UnitType.Cruiser, 1);
        Map<String, Map<UnitType, Integer>> available =
                Map.of(line.positions.get(0), Map.of(UnitType.Dreadnought, 2, UnitType.Cruiser, 1));

        Map<String, Map<UnitType, Integer>> chosen = MoveBudget.allocate(game, seat, line.at(3), available, COST, 10);

        assertThat(chosen.get(line.positions.get(0)))
                .containsEntry(UnitType.Dreadnought, 2)
                .containsEntry(UnitType.Cruiser, 1);
    }

    // The pace setter is kept when fleet supply trims the stack, or nothing from the system could arrive.
    @Test
    void fleetSupplyKeepsThePaceSetter() {
        unlock("letnevbt");
        line.ships(0, UnitType.Dreadnought, 2);
        line.ships(0, UnitType.Cruiser, 1);
        Map<String, Map<UnitType, Integer>> available =
                Map.of(line.positions.get(0), Map.of(UnitType.Dreadnought, 2, UnitType.Cruiser, 1));

        Map<String, Map<UnitType, Integer>> chosen = MoveBudget.allocate(game, seat, line.at(2), available, COST, 2);

        assertThat(chosen.get(line.positions.get(0)))
                .containsEntry(UnitType.Cruiser, 1)
                .containsEntry(UnitType.Dreadnought, 1);
    }

    // Letnev ruling II: a Fighter II (move 2) can set the pace for a dreadnought.
    @Test
    void aFighterTwoSetsThePace() {
        unlock("letnevbt");
        seat.removeOwnedUnitByID("fighter");
        seat.addOwnedUnitByID("fighter2");
        line.ships(0, UnitType.Dreadnought, 1);
        line.ships(0, UnitType.Fighter, 1);
        Tile target = line.at(2);

        assertThat(MoveBudget.boostsNeeded(
                        game, seat, target, List.of(from(0, UnitType.Dreadnought, 1), from(0, UnitType.Fighter, 1))))
                .isZero();
        assertThat(MoveBudget.boostsNeeded(game, seat, target, List.of(from(0, UnitType.Dreadnought, 1))))
                .isEqualTo(1);
    }

    // Cargo is not checked for movement: infantry aboard a carrier never need a boost of their own.
    @Test
    void infantryAboardNeedNoBoost() {
        line.ships(0, UnitType.Carrier, 1);
        line.test.units(line.at(0), "space", seat, UnitType.Infantry, 2);
        Tile target = line.at(1);
        List<Departure> plan = List.of(
                from(0, UnitType.Carrier, 1),
                from(0, UnitType.Infantry, 2),
                new Departure(target.getPosition(), UnitType.Infantry, 1));

        assertThat(MoveBudget.boostsNeeded(game, seat, target, plan)).isZero();
        seat.addPlanet("tempesta");
        assertThat(MoveBudget.boostsNeeded(game, seat, target, plan))
                .isLessThanOrEqualTo(MoveBudget.oneShip(game, seat, target) - 1);
    }

    @Test
    void aShipWithNoPathIsNeverFeasible() {
        line.ships(0, UnitType.Cruiser, 1);
        Tile island = line.test.place("25", line.islandPosition());

        assertThat(MoveBudget.boostsNeeded(game, seat, island, List.of(from(0, UnitType.Cruiser, 1))))
                .isEqualTo(Integer.MAX_VALUE);
    }

    // flankspeedBoost survives until the next activation; another player's AI must not count it.
    @Test
    void aFlankSpeedStillStoredFromThePreviousTurnDoesNotCount() {
        line.ships(0, UnitType.Carrier, 1);
        Tile target = line.at(2);
        game.setStoredValue(ShipSpeed.FLANK_SPEED_BOOST, "1");
        line.test.isActive(line.test.sol, "action");
        game.setActiveSystem(target.getPosition());
        game.setStoredValue("currentActionSummary" + line.test.sol.getFaction(), " Activated " + target.getPosition());

        assertThat(MoveBudget.feasible(game, seat, target, List.of(from(0, UnitType.Carrier, 1))))
                .isFalse();
    }

    // A plan that relied on Flank Speed is no longer feasible once a Sabotage clears the stored value.
    @Test
    void aSabotagedFlankSpeedMakesThePlanInfeasible() {
        line.ships(0, UnitType.Carrier, 1);
        Tile target = line.at(2);
        List<Departure> carrier = List.of(from(0, UnitType.Carrier, 1));
        activate(target);
        game.setStoredValue(ShipSpeed.FLANK_SPEED_BOOST, "1");
        assertThat(MoveBudget.feasible(game, seat, target, carrier)).isTrue();

        game.removeStoredValue(ShipSpeed.FLANK_SPEED_BOOST);
        assertThat(MoveBudget.feasible(game, seat, target, carrier)).isFalse();
    }

    // Two rounds from a nebula: the first leg starts capped at 1, the second at the cruiser's full 2.
    @Test
    void twoLegsFromANebulaAreOnePlusFullSpeed() {
        line.swap(0, "42");
        line.ships(0, UnitType.Cruiser, 1);

        assertThat(MoveBudget.legBudget(game, seat, UnitType.Cruiser, line.at(0), null, false))
                .isEqualTo(1);
        assertThat(within(line.at(3), Journey.rounds(2))).containsKey(line.positions.get(0));
        assertThat(within(line.at(4), Journey.rounds(2))).isEmpty();
    }

    // Without a tactical token this round, the first of two legs waits a round and only one leg is left.
    @Test
    void noTacticalTokenLeavesOneLeg() {
        line.ships(0, UnitType.Cruiser, 1);
        assertThat(within(line.at(4), Journey.rounds(2))).isNotEmpty();

        seat.setTacticalCC(0);
        assertThat(within(line.at(4), Journey.rounds(2))).isEmpty();
        assertThat(within(line.at(2), Journey.rounds(2))).isNotEmpty();
    }

    // Thunder's Edge Warfare: "Perform a tactical action in any system ... even if the system already has your command
    // token in it". Holding it unplayed lets a seat activate a system it already holds a token in, even with no tactic
    // tokens left.
    @Test
    void thundersEdgeWarfareActivatesASystemThatHoldsTheSeatsToken() {
        line.ships(0, UnitType.Cruiser, 1);
        line.at(1).addCC(ti4.model.Mapper.getCCID(seat.getColor()));
        assertThat(within(line.at(1), Journey.thisAction())).isEmpty();

        game.setStrategyCardSet("te");
        seat.setTacticalCC(0);
        seat.addSC(WARFARE_INITIATIVE);
        assertThat(within(line.at(1), Journey.thisAction())).isNotEmpty();

        game.setSCPlayed(WARFARE_INITIATIVE, true);
        assertThat(within(line.at(1), Journey.thisAction())).isEmpty();
    }

    // A screened system is a wall for this search: with the corridor's only middle system screened, nothing arrives.
    @Test
    void aScreenedSystemOnTheOnlyPathStopsTheFleet() {
        line.ships(0, UnitType.Cruiser, 1);
        assertThat(MoveBudget.shipsWithin(game, seat, line.at(2), Journey.thisAction(), tile -> true, COST, 10))
                .isNotEmpty();

        assertThat(MoveBudget.shipsWithin(
                        game,
                        seat,
                        line.at(2),
                        Journey.thisAction(),
                        tile -> true,
                        COST,
                        10,
                        java.util.Set.of(line.positions.get(1))))
                .isEmpty();
    }

    // A ship not yet built moves one leg per later round, each with its own Gravity Drive +1 (the dock holds the token
    // after production, so no leg is this round): a carrier covers (1 + 1) per leg, 4 systems over two rounds.
    @Test
    void laterRoundBudgetGivesGravityDriveOnEveryLeg() {
        assertThat(MoveBudget.laterRoundBudget(game, seat, UnitType.Carrier, line.at(0), line.at(4), 2))
                .isEqualTo(2);

        seat.addTech("gd");
        assertThat(MoveBudget.laterRoundBudget(game, seat, UnitType.Carrier, line.at(0), line.at(4), 2))
                .isEqualTo(4);
        assertThat(MoveBudget.laterRoundCeiling(game, seat, UnitType.Carrier, line.at(0), 2))
                .isEqualTo(4);
    }

    // An assumed Gravity Drive (research scenarios) counts in later rounds too, once, and never twice when owned.
    @Test
    void assumedGravityDriveCountsOnceInALaterRound() {
        assertThat(MoveBudget.oneShipInLaterRound(seat, null, true)).isEqualTo(1);

        seat.addTech("gd");
        assertThat(MoveBudget.oneShipInLaterRound(seat, null, true)).isEqualTo(1);
    }

    @Test
    void aPassedSeatMovesNothingThisAction() {
        line.ships(0, UnitType.Cruiser, 1);
        assertThat(within(line.at(2), Journey.thisAction())).isNotEmpty();

        seat.setPassed(true);
        assertThat(within(line.at(2), Journey.thisAction())).isEmpty();
        assertThat(within(line.at(2), Journey.nextRound())).isNotEmpty();
    }

    // Call of the Void is +1 for the action that activates a Fracture system, so two legs gain it once.
    @Test
    void callOfTheVoidCountsOnceOverTwoLegs() {
        FractureService.spawnFracture(null, game);
        game.removeTile(line.positions.get(2));
        game.removeTile(line.positions.get(3));
        game.removeTile(line.positions.get(4));
        line.at(1).addToken(Constants.TOKEN_INGRESS, "space");
        line.ships(0, UnitType.Carrier, 1);
        Tile threeAway = game.getTileByPosition("frac3");
        Tile styx = game.getTileByPosition("frac4");
        assertThat(within(threeAway, Journey.rounds(2))).isEmpty();

        game.setCallOfTheVoidMode(true);
        assertThat(within(threeAway, Journey.rounds(2))).isNotEmpty();
        assertThat(within(styx, Journey.rounds(2))).isEmpty();
    }

    // From the ingress system a carrier needs three moves to Styx. A known Fracture staging system gives Al'Raith on
    // the second leg; an unknown one does not, unless the threat view takes the best staging system in reach.
    @Test
    void alRaithCountsOnTheSecondLegOnlyFromAKnownFractureStagingSystem() {
        FractureService.spawnFracture(null, game);
        game.removeTile(line.positions.get(2));
        game.removeTile(line.positions.get(3));
        game.removeTile(line.positions.get(4));
        line.at(1).addToken(Constants.TOKEN_INGRESS, "space");
        line.ships(1, UnitType.Carrier, 1);
        unlock("cabalbt");
        Tile styx = game.getTileByPosition("frac4");

        assertThat(within(styx, Journey.rounds(2))).isEmpty();
        assertThat(within(styx, Journey.rounds(2).via(game.getTileByPosition("frac2"))))
                .isNotEmpty();
        assertThat(within(styx, Journey.rounds(2).asThreat())).isNotEmpty();
    }

    // A known staging system is activated too: with Call of the Void, a Fracture staging system gives the first leg
    // its own +1, and the Fracture target gives the second leg another, so a carrier covers four systems in two legs.
    @Test
    void aKnownFractureStagingSystemGivesTheFirstLegCallOfTheVoid() {
        FractureService.spawnFracture(null, game);
        game.removeTile(line.positions.get(2));
        game.removeTile(line.positions.get(3));
        game.removeTile(line.positions.get(4));
        line.at(1).addToken(Constants.TOKEN_INGRESS, "space");
        line.ships(0, UnitType.Carrier, 1);
        Tile styx = game.getTileByPosition("frac4");
        Map<String, Integer> fromHome = MovementGraph.distances(game, seat, AiTestGame.HOME, 4);
        assertThat(fromHome).containsEntry(styx.getPosition(), 4);
        Tile staging = game.getTileMap().values().stream()
                .filter(Tile::isFracture)
                .filter(tile -> fromHome.getOrDefault(tile.getPosition(), 0) == 2)
                .filter(tile -> MovementGraph.distances(game, seat, tile.getPosition(), 2)
                        .containsKey(styx.getPosition()))
                .findFirst()
                .orElseThrow();

        game.setCallOfTheVoidMode(true);
        assertThat(within(styx, Journey.rounds(2))).isEmpty();
        assertThat(within(styx, Journey.rounds(2).via(staging))).isNotEmpty();
    }

    @Test
    void shipsWithinKeepsTheMostValuableShipsInFleetSupply() {
        line.ships(0, UnitType.Cruiser, 1);
        line.ships(1, UnitType.Destroyer, 2);
        line.ships(1, UnitType.Dreadnought, 1);

        Map<String, Map<UnitType, Integer>> chosen =
                MoveBudget.shipsWithin(game, seat, line.at(2), Journey.thisAction(), tile -> true, COST, 2);

        Map<UnitType, Integer> expected = new EnumMap<>(UnitType.class);
        expected.put(UnitType.Cruiser, 1);
        assertThat(chosen.get(line.positions.get(0))).isEqualTo(expected);
        assertThat(chosen.get(line.positions.get(1))).containsOnlyKeys(UnitType.Dreadnought);
    }

    @Test
    void searchBudgetCoversEveryBoostThatCouldApply() {
        line.ships(0, UnitType.Cruiser, 1);
        assertThat(MoveBudget.searchBudget(game, seat, line.at(0))).isEqualTo(2);

        seat.addTech("gd");
        seat.addTech("as");
        assertThat(MoveBudget.searchBudget(game, seat, line.at(0))).isEqualTo(4);
        assertThat(AiTestGame.HOME).isEqualTo(line.positions.getFirst());
    }

    // A carrier (move 1) crosses the rift one system out and reaches two systems out (LRR 41.1). The move is legal,
    // and the carrier is the one ship that rolls.
    @Test
    void aRiftRouteIsFeasibleAndRollsOnce() {
        line.swap(1, "41");
        line.ships(0, UnitType.Carrier, 1);
        List<Departure> carrier = List.of(from(0, UnitType.Carrier, 1));

        assertThat(MoveBudget.feasible(game, seat, line.at(2), carrier)).isTrue();
        assertThat(MoveBudget.riftExits(game, seat, line.at(2), carrier))
                .containsExactly(new Departure(AiTestGame.HOME, UnitType.Carrier, 1));
        assertThat(MoveBudget.feasible(game, seat, line.at(3), carrier)).isFalse();
    }

    // Two ways to a system two out: through a rift (effective 1) or around it (2). A move-1 carrier must roll
    // unless Gravity Drive pays for the calm path, and then nothing rolls.
    @Test
    void aGravityDriveBoostIsSpentToAvoidARoll() {
        Fork fork = new Fork();
        fork.test.units(
                fork.test.game.getTileByPosition(AiTestGame.HOME), "space", fork.test.nekro, UnitType.Carrier, 1);
        List<Departure> carrier = List.of(new Departure(AiTestGame.HOME, UnitType.Carrier, 1));

        assertThat(MoveBudget.riftExits(fork.test.game, fork.test.nekro, fork.target, carrier))
                .containsExactly(new Departure(AiTestGame.HOME, UnitType.Carrier, 1));

        fork.test.nekro.addTech("gd");
        assertThat(MoveBudget.feasible(fork.test.game, fork.test.nekro, fork.target, carrier))
                .isTrue();
        assertThat(MoveBudget.riftExits(fork.test.game, fork.test.nekro, fork.target, carrier))
                .isEmpty();
    }

    // Gravity Drive lifts one ship around the rift. Rolling the carrier would put two of its three mechs at risk as
    // well (only one fits the dreadnought), so the dreadnought takes the roll and the carrier goes the calm way.
    @Test
    void aRiftRollGoesToTheShipWhoseLossCostsLeastWithItsCargo() {
        Fork fork = new Fork();
        fork.test.nekro.addTech("gd");
        Tile home = fork.test.game.getTileByPosition(AiTestGame.HOME);
        fork.test.units(home, "space", fork.test.nekro, UnitType.Dreadnought, 1);
        fork.test.units(home, "space", fork.test.nekro, UnitType.Carrier, 1);
        fork.test.units(home, "space", fork.test.nekro, UnitType.Mech, 3);
        List<Departure> moves = List.of(
                new Departure(AiTestGame.HOME, UnitType.Dreadnought, 1),
                new Departure(AiTestGame.HOME, UnitType.Carrier, 1),
                new Departure(AiTestGame.HOME, UnitType.Mech, 3));

        assertThat(MoveBudget.feasible(fork.test.game, fork.test.nekro, fork.target, moves))
                .isTrue();
        assertThat(MoveBudget.riftExits(fork.test.game, fork.test.nekro, fork.target, moves))
                .containsExactly(new Departure(AiTestGame.HOME, UnitType.Dreadnought, 1));
    }

    // With room for one ship the calm cruiser goes; the cruiser parked in a rift (a forced roll, worth 0.4 of itself)
    // only comes when there is room for both.
    @Test
    void allocationTakesARiftShipOnlyAfterCalmShips() {
        line.swap(3, "41");
        line.ships(0, UnitType.Cruiser, 1);
        line.ships(3, UnitType.Cruiser, 1);
        Map<String, Map<UnitType, Integer>> available = Map.of(
                line.positions.get(0), Map.of(UnitType.Cruiser, 1),
                line.positions.get(3), Map.of(UnitType.Cruiser, 1));

        assertThat(MoveBudget.allocate(game, seat, line.at(2), available, COST, 1))
                .containsOnlyKeys(line.positions.get(0));
        assertThat(MoveBudget.allocate(game, seat, line.at(2), available, COST, 2))
                .containsOnlyKeys(line.positions.get(0), line.positions.get(3));
    }

    // Gravleash: the leashed dreadnought follows the cruiser through the rift, so it rolls with it.
    @Test
    void aGravleashGroupFollowsItsPacersRoll() {
        line.swap(1, "41");
        unlock("letnevbt");
        line.ships(0, UnitType.Dreadnought, 1);
        line.ships(0, UnitType.Cruiser, 1);
        List<Departure> group = List.of(from(0, UnitType.Dreadnought, 1), from(0, UnitType.Cruiser, 1));

        assertThat(MoveBudget.feasible(game, seat, line.at(3), group)).isTrue();
        assertThat(MoveBudget.riftExits(game, seat, line.at(3), group))
                .containsExactlyInAnyOrder(
                        new Departure(AiTestGame.HOME, UnitType.Dreadnought, 1),
                        new Departure(AiTestGame.HOME, UnitType.Cruiser, 1));
        assertThat(MoveBudget.riftExits(game, seat, line.at(1), group)).isEmpty();
    }

    // Note III lets the player choose which ship carries what, so cargo rides the calm ships first; only what does not
    // fit is at risk with the rolling carrier.
    @Test
    void expectedLossCountsCargoBeyondCalmCapacity() {
        List<Departure> moves =
                List.of(from(0, UnitType.Carrier, 2), from(0, UnitType.Infantry, 6), from(0, UnitType.Fighter, 2));
        List<Departure> rolling = List.of(from(0, UnitType.Carrier, 1));

        assertThat(RiftRisk.expectedLoss(seat, line.at(2), moves, rolling, COST))
                .isCloseTo(0.3 * 3 + 0.3 * 4 * 0.5, org.assertj.core.data.Offset.offset(1e-9));

        line.swap(1, "41");
        line.ships(0, UnitType.Carrier, 1);
        line.test.units(line.at(0), "space", seat, UnitType.Infantry, 2);
        List<Departure> loaded = List.of(from(0, UnitType.Carrier, 1), from(0, UnitType.Infantry, 2));
        assertThat(RiftRisk.expectedLoss(game, seat, line.at(2), loaded, COST))
                .isCloseTo(0.3 * (3 + 2 * 0.5), org.assertj.core.data.Offset.offset(1e-9));
        assertThat(RiftRisk.rollingShips(game, seat, line.at(2), loaded)).isEqualTo(1);
    }

    private void fighterTwo() {
        seat.removeOwnedUnitByID("fighter");
        seat.addOwnedUnitByID("fighter2");
    }

    // Fighter II has move 2 and may move without being transported, so three of them reach two systems out.
    @Test
    void aFighterIIGroupTwoAwayIsFeasible() {
        fighterTwo();
        line.ships(0, UnitType.Fighter, 3);
        List<Departure> wing = List.of(from(0, UnitType.Fighter, 3));

        assertThat(MoveBudget.boostsNeeded(game, seat, line.at(2), wing)).isZero();
        assertThat(MoveBudget.feasible(game, seat, line.at(2), wing)).isTrue();
        assertThat(MoveBudget.canJoin(game, seat, line.at(0), line.at(2), UnitType.Fighter))
                .isTrue();
    }

    // A Fighter I has no move value: without a carrier it goes nowhere.
    @Test
    void fighterIsCannotFlyAlone() {
        line.ships(0, UnitType.Fighter, 2);
        List<Departure> wing = List.of(from(0, UnitType.Fighter, 2));

        assertThat(MoveBudget.boostsNeeded(game, seat, line.at(1), wing)).isEqualTo(Integer.MAX_VALUE);
        assertThat(MoveBudget.canJoin(game, seat, line.at(0), line.at(1), UnitType.Fighter))
                .isFalse();
    }

    // Each flying fighter is its own ship for range: three systems out needs one boost per Fighter II.
    @Test
    void fighterIIsThreeAwayNeedABoost() {
        fighterTwo();
        seat.addTech("gd");
        line.ships(0, UnitType.Fighter, 2);

        assertThat(MoveBudget.boostsNeeded(game, seat, line.at(3), List.of(from(0, UnitType.Fighter, 1))))
                .isEqualTo(1);
        assertThat(MoveBudget.feasible(game, seat, line.at(3), List.of(from(0, UnitType.Fighter, 1))))
                .isTrue();
        assertThat(MoveBudget.boostsNeeded(game, seat, line.at(3), List.of(from(0, UnitType.Fighter, 2))))
                .isEqualTo(2);
        assertThat(MoveBudget.feasible(game, seat, line.at(3), List.of(from(0, UnitType.Fighter, 2))))
                .isFalse();
    }

    // Fighters inside the carrier's capacity are transported (LRR 95), so even Fighter Is need no range; a fifth one
    // beyond its 4 slots would have to fly.
    @Test
    void carriedFightersNeedNoRange() {
        line.ships(0, UnitType.Carrier, 1);
        line.ships(0, UnitType.Fighter, 5);

        assertThat(MoveBudget.boostsNeeded(
                        game, seat, line.at(1), List.of(from(0, UnitType.Carrier, 1), from(0, UnitType.Fighter, 4))))
                .isZero();
        assertThat(MoveBudget.boostsNeeded(
                        game, seat, line.at(1), List.of(from(0, UnitType.Carrier, 1), from(0, UnitType.Fighter, 5))))
                .isEqualTo(Integer.MAX_VALUE);

        fighterTwo();
        assertThat(MoveBudget.boostsNeeded(
                        game, seat, line.at(1), List.of(from(0, UnitType.Carrier, 1), from(0, UnitType.Fighter, 5))))
                .isZero();
    }

    // Letnev ruling II: a flying Fighter II sets the pace for the dreadnought, but fighters are never leashed, so a
    // second flying fighter needs its own boost to go beyond its move.
    @Test
    void aFlyingFighterIISetsTheGravleashPace() {
        unlock("letnevbt");
        fighterTwo();
        line.ships(0, UnitType.Dreadnought, 1);
        line.ships(0, UnitType.Fighter, 3);
        List<Departure> group = List.of(from(0, UnitType.Dreadnought, 1), from(0, UnitType.Fighter, 3));

        assertThat(MoveBudget.boostsNeeded(game, seat, line.at(2), group)).isZero();
        seat.addTech("gd");
        assertThat(MoveBudget.boostsNeeded(game, seat, line.at(3), group)).isEqualTo(2);
        assertThat(MoveBudget.feasible(game, seat, line.at(3), group)).isFalse();
        assertThat(MoveBudget.feasible(
                        game,
                        seat,
                        line.at(3),
                        List.of(from(0, UnitType.Dreadnought, 1), from(0, UnitType.Fighter, 2))))
                .isTrue();
    }

    // Gravity rift note V beta: a Fighter II that moves itself out of a rift rolls; the four aboard the carrier only
    // share their carrier's roll.
    @Test
    void aFlyingFighterIIRollsInARiftButCarriedOnesDoNot() {
        fighterTwo();
        line.swap(1, "41");
        line.ships(0, UnitType.Carrier, 1);
        line.ships(0, UnitType.Fighter, 6);
        List<Departure> moves = List.of(from(0, UnitType.Carrier, 1), from(0, UnitType.Fighter, 6));

        assertThat(MoveBudget.feasible(game, seat, line.at(2), moves)).isTrue();
        assertThat(MoveBudget.riftExits(game, seat, line.at(2), moves))
                .containsExactlyInAnyOrder(
                        new Departure(AiTestGame.HOME, UnitType.Carrier, 1),
                        new Departure(AiTestGame.HOME, UnitType.Fighter, 2));
        assertThat(RiftRisk.expectedLoss(game, seat, line.at(2), moves, COST))
                .isCloseTo(0.3 * 3 + 0.3 * 2 * 0.5 + 0.3 * 4 * 0.5, org.assertj.core.data.Offset.offset(1e-9));
    }

    // Spare Fighter IIs fly beside the carrier while fleet supply has room: with one slot after the carrier, the
    // allocation flies one fighter beyond the four aboard.
    @Test
    void allocationFliesSpareFighterIIsWithinFleetSupply() {
        fighterTwo();
        line.ships(0, UnitType.Carrier, 1);
        line.ships(0, UnitType.Fighter, 6);
        Map<String, Map<UnitType, Integer>> available =
                Map.of(line.positions.get(0), Map.of(UnitType.Carrier, 1, UnitType.Fighter, 6));

        assertThat(MoveBudget.allocate(game, seat, line.at(1), available, COST, 2)
                        .get(line.positions.get(0)))
                .containsEntry(UnitType.Carrier, 1)
                .containsEntry(UnitType.Fighter, 1);
        assertThat(MoveBudget.allocate(game, seat, line.at(1), available, COST, 3)
                        .get(line.positions.get(0)))
                .containsEntry(UnitType.Fighter, 2);
    }

    /** Home, a gravity rift and a plain system both next to home, and a target next to both of them. */
    private static final class Fork {
        final AiTestGame test = new AiTestGame();
        Tile target;

        Fork() {
            test.nekroHome();
            List<String> around = neighbours(AiTestGame.HOME);
            for (String first : around) {
                for (String candidate : neighbours(first)) {
                    if (AiTestGame.HOME.equals(candidate) || around.contains(candidate)) continue;
                    List<String> shared = neighbours(candidate).stream()
                            .filter(around::contains)
                            .toList();
                    if (shared.size() < 2) continue;
                    test.place("41", shared.get(0));
                    test.place("19", shared.get(1));
                    target = test.place("20", candidate);
                    return;
                }
            }
            throw new IllegalStateException("no fork");
        }

        private static List<String> neighbours(String position) {
            return ti4.model.MapPositionMapper.getAdjacentTilePositions(position).stream()
                    .filter(each -> !"x".equals(each))
                    .toList();
        }
    }

    private Map<String, Map<UnitType, Integer>> within(Tile target, Journey journey) {
        return MoveBudget.shipsWithin(game, seat, target, journey, tile -> true, COST, 10);
    }
}
