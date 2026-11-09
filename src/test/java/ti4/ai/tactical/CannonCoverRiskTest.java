package ti4.ai.tactical;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import ti4.ai.AiExperiments;
import ti4.ai.AiExperiments.Experiment;
import ti4.ai.AiTestGame;
import ti4.ai.eval.CannonDice;
import ti4.ai.eval.DiceOdds;
import ti4.ai.scoring.ObjectiveValue;
import ti4.ai.tactical.IncomingForces.Horizon;
import ti4.ai.tactical.TacticalPlan.Kind;
import ti4.ai.tactical.TacticalPlan.UnitMove;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.model.MapPositionMapper;
import ti4.testUtils.BaseTi4Test;

// CANNON_COVER_RISK: space cannon cover (LRR 77) is a risk to price, never a wall. Each converted site is checked with
// the experiment on (the plan or chance survives at its odds) and off (the old hard stop for a control seat).
class CannonCoverRiskTest extends BaseTi4Test {

    private static final double EXACT = 1e-9;
    private static final double VP = ObjectiveValue.VICTORY_POINT_VALUE;

    private static void control(Player seat) {
        AiExperiments.disable(seat, EnumSet.of(Experiment.CANNON_COVER_RISK));
    }

    // Movement planning (afterCannon): one PDS II die at 5+ gives a lone carrier a 40% delivery, under the old 50%
    // viability cut. The treatment seat still expands, at the delivered share of the gain less the expected loss.
    static final class Expansion {
        final AiTestGame test = new AiTestGame();
        final Tile home;
        final String target;
        final Tile solHome;

        Expansion() {
            test.game.setRound(3);
            home = test.nekroHome();
            test.sol.removeTech("amd");
            target = MapPositionMapper.getAdjacentTilePositions(AiTestGame.HOME).stream()
                    .filter(position -> !"x".equals(position))
                    .findFirst()
                    .orElseThrow();
            test.place("19", target);
            solHome = test.solHomeBeside(target);
            test.nekro.setSecret("mp");
            test.units(home, "space", test.nekro, UnitType.Carrier, 1);
            test.units(home, "space", test.nekro, UnitType.Infantry, 1);
        }

        void cover(int pds) {
            test.sol.removeOwnedUnitByID("pds");
            test.sol.addOwnedUnitByID("pds2");
            test.units(solHome, "jord", test.sol, UnitType.Pds, pds);
        }

        Optional<TacticalPlan> expansion() {
            return TacticalPlanner.forTarget(test.game, test.nekro, target)
                    .filter(plan ->
                            plan.kind() == Kind.EXPAND && plan.landings().containsKey("wellon"));
        }
    }

    @Test
    void aLoneCarrierExpandsUnderCoverAtItsDeliveryOdds() {
        Expansion board = new Expansion();
        double open = board.expansion().orElseThrow().score();
        board.cover(1);

        TacticalPlan covered = board.expansion().orElseThrow();

        assertThat(covered.score()).isLessThan(open);
        assertThat(covered.score()).isLessThanOrEqualTo(0.4 * open);
    }

    @Test
    void aControlSeatStillRefusesTheLoneCarrierUnderCover() {
        Expansion board = new Expansion();
        board.cover(1);
        control(board.test.nekro);

        assertThat(board.expansion()).isEmpty();
    }

    // Strike odds (StrikeEvaluator): four PDS II dice at 5+ round to enough hits to sink a carrier and a cruiser, so
    // the
    // old estimate called the strike hopeless. Nothing defends the space, so a strike that survives the dice wins it:
    // the space win is exactly the chance fewer than two of the four dice hit.
    static final class Strike4 {
        final DefenseScenario board = new DefenseScenario();
        final Tile target;

        Strike4() {
            board.test.nekro.setFleetCC(10);
            target = board.test.place("26", board.otherNeighbourOfHome());
            board.sardakk.addPlanet("lodor");
            board.sardakk.removeOwnedUnitByID("pds");
            board.sardakk.addOwnedUnitByID("pds2");
            board.test.units(target, "lodor", board.sardakk, UnitType.Pds, 4);
        }

        StrikeEstimate estimate() {
            List<UnitMove> moves = List.of(
                    new UnitMove(AiTestGame.HOME, "space", UnitType.Carrier, 1),
                    new UnitMove(AiTestGame.HOME, "space", UnitType.Cruiser, 1));
            Strike strike = Strike.of(
                    board.test.nekro,
                    target,
                    board.sardakk,
                    List.of(board.home),
                    moves,
                    new StrikeRules(false, 0.5, true, false));
            return StrikeEvaluator.estimate(board.test.game, board.test.nekro, strike);
        }
    }

    @Test
    void aStrikeUnderHeavyCoverKeepsItsSurvivalChance() {
        Strike4 strike = new Strike4();
        double wiped = DiceOdds.atLeast(
                CannonDice.againstShipsOf(strike.board.test.game, strike.board.test.nekro, strike.target), 2);

        StrikeEstimate estimate = strike.estimate();

        assertThat(wiped).isBetween(0.5, 1.0);
        assertThat(estimate.spaceWin()).isCloseTo(1 - wiped, within(EXACT));
        assertThat(estimate.ourLossChance()).isGreaterThanOrEqualTo(wiped);
    }

    @Test
    void aControlSeatStillCallsTheCoveredStrikeHopeless() {
        Strike4 strike = new Strike4();
        control(strike.board.test.nekro);

        StrikeEstimate estimate = strike.estimate();

        assertThat(estimate.spaceWin()).isZero();
        assertThat(estimate.ourLossChance()).isEqualTo(1.0);
    }

    // Threat forecast (DefenseOutlook): Nekro's home holds only a PDS (6+, one die) and a lone Sardakk cruiser waits
    // next door. The rounded half hit used to sink the cruiser every time; it gets through half the time.
    private static double raidSucceeds(DefenseScenario board) {
        DefenseOutlook defense = new DefenseOutlook(board.test.game, board.test.nekro);
        return defense.chances(board.sardakk, board.home, Horizon.IMMEDIATE, defense.defendersAt(board.home))
                .space();
    }

    private static DefenseScenario homeUnderOnePds() {
        DefenseScenario board = new DefenseScenario();
        board.sardakk.setTacticalCC(3);
        board.raidingFleet(0, 1, 0);
        board.test.units(board.home, "mordaiii", board.test.nekro, UnitType.Pds, 1);
        return board;
    }

    @Test
    void aLoneRaiderThroughTheSeatsCannonGetsThroughAtItsSurvivalOdds() {
        DefenseScenario board = homeUnderOnePds();
        double hit = CannonDice.fromShooter(board.test.game, board.home, board.test.nekro, board.sardakk).stream()
                .mapToDouble(Double::doubleValue)
                .sum();

        assertThat(hit).isCloseTo(0.5, within(EXACT));
        assertThat(raidSucceeds(board)).isCloseTo(1 - hit, within(EXACT));
    }

    @Test
    void aControlSeatStillTreatsTheRoundedHitAsAWall() {
        DefenseScenario board = homeUnderOnePds();
        control(board.test.nekro);

        assertThat(raidSucceeds(board)).isZero();
    }

    // Occupy the Seat of the Empire (3 ships in the Mecatol Rex system) through cover (SeatCover). A cruiser on
    // Mecatol and a carrier with one fighter arriving make exactly three ships; one PDS II die at 5+ breaks the
    // objective 60% of the time, so the seat is charged 60% of a point. A dreadnought in place of the cruiser takes
    // the hit on its sustain damage (LRR 80), and the objective is safe.
    static final class Seat {
        final AiTestGame test = new AiTestGame();
        final Tile rex;
        final List<String> ring;
        final Tile launch;

        Seat(UnitType holder) {
            this(holder, 1, 1);
        }

        Seat(UnitType holder, int holders, int cannons) {
            test.game.setPhaseOfGame("action");
            test.nekroHome();
            test.nekro.setTacticalCC(3);
            test.sol.removeTech("amd");
            rex = test.place("18", "000");
            test.nekro.addPlanet("mr");
            test.nekro.setSecret("ose");
            ring = MapPositionMapper.getAdjacentTilePositions("000").stream()
                    .filter(position -> !"x".equals(position))
                    .toList();
            test.units(rex, "space", test.nekro, holder, holders);
            launch = test.place("26", ring.get(0));
            test.units(launch, "space", test.nekro, UnitType.Carrier, 1);
            test.units(launch, "space", test.nekro, UnitType.Fighter, 1);
            test.place("01", ring.get(3));
            test.sol.addPlanet("jord");
            test.sol.removeOwnedUnitByID("pds");
            test.sol.addOwnedUnitByID("pds2");
            test.units(test.game.getTileByPosition(ring.get(3)), "jord", test.sol, UnitType.Pds, cannons);
        }

        double shortfall() {
            return shortfall(List.of(
                    new UnitMove(launch.getPosition(), "space", UnitType.Carrier, 1),
                    new UnitMove(launch.getPosition(), "space", UnitType.Fighter, 1)));
        }

        double shortfall(List<UnitMove> moves) {
            TacticalPlanner.Context context = new TacticalPlanner.Context(test.game, test.nekro);
            return SeatCover.seatShortfall(
                    test.game,
                    test.nekro,
                    rex,
                    context.objectives(),
                    context.footprintAfter(rex, moves, List.of()),
                    moves);
        }
    }

    @Test
    void theSeatIsChargedTheChanceTheCannonBreaksIt() {
        Seat seat = new Seat(UnitType.Cruiser);

        assertThat(seat.shortfall()).isCloseTo(0.6 * VP, within(EXACT));
        assertThat(SeatCover.completesAtRisk(seat.test.game, seat.test.nekro, seat.rex, List.of()))
                .isTrue();
    }

    @Test
    void aSustainingHolderKeepsTheSeatThroughOneDie() {
        Seat seat = new Seat(UnitType.Dreadnought);

        assertThat(seat.shortfall()).isZero();
    }

    // The objective is already met before the move: three cruisers hold Mecatol Rex. Space cannon offense fires at
    // every ship of the active player in the active system (LRR 77.2-77.5), not just the arrivals, so moving a
    // fourth ship in under two PDS II dice (5+, 0.6 each) still risks the point: 2 hits leave 2 ships, P = 0.36.
    @Test
    void movingIntoAnAlreadyHeldSeatStillRisksThePoint() {
        Seat seat = new Seat(UnitType.Cruiser, 3, 2);

        double charge = seat.shortfall(List.of(new UnitMove(seat.launch.getPosition(), "space", UnitType.Carrier, 1)));

        assertThat(charge).isGreaterThan(0);
        assertThat(charge).isCloseTo(0.36 * VP, within(EXACT));
    }
}
