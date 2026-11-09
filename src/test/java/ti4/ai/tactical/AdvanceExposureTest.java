package ti4.ai.tactical;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.tactical.TacticalPlan.Kind;
import ti4.ai.tactical.TacticalPlan.UnitMove;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.model.MapPositionMapper;
import ti4.testUtils.BaseTi4Test;

// An advance parks a carrier and its infantry in an empty system to reach planets next activation. Instead of refusing
// any system an enemy ship can reach, the planner prices the stop at the loss it expects before its next activation:
//   P(attack) x loss if attacked.
// Loss if attacked is the chance the enemy wins the space combat times the cost of what sits in space. When an adjacent
// system is a safe retreat it is capped by the expected cost of the first round's hits (sustain first, cheapest units
// first), since the group can announce a retreat and leave after one round. A retreat is not safe into the origin the
// move empties, nor into a system where the same enemy would beat the retreating group.
// P(attack) is DefenseOutlook.attackLikelihood: the enemy's appetite (our loss minus its own expected losses and the
// cost of a tactical action), smoothed by a softplus so it never jumps at zero, as a share appetite / (appetite +
// alternative), where the alternative is the best free planets its carriers can reach, at least 3. It is scaled by the
// existing early-round damping (0.3 in round 1, 0.6 in round 2), the enemy's aggression and any ceasefire.
class AdvanceExposureTest extends BaseTi4Test {

    private AiTestGame test;
    private Tile home;
    private String neighbour;
    private Tile enemyPost;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.game.setRound(1);
        test.nekro.setTacticalCC(3);
        home = test.nekroHome();
        neighbour = AiTestGame.neighbourOf(AiTestGame.HOME);
        test.place("47", neighbour);
        test.place("27", twoSystemsFromHome());
        enemyPost = test.place("48", besideNeighbourAwayFromHome());
        test.units(home, "space", test.nekro, UnitType.Carrier, 1);
        test.units(home, "mordaiii", test.nekro, UnitType.Infantry, 3);
    }

    // Round 3, two dreadnoughts and two cruisers next door (three fit Sol's fleet supply). A lone carrier there dies to
    // the first hit 90% of the time and is worth an action to Sol, so the expected loss outweighs the step forward.
    @Test
    void aLoneCarrierDoesNotStopBesideAStrongFleet() {
        test.game.setRound(3);
        strongFleetNextDoor();

        assertThat(advance().filter(plan -> plan.score() >= 0)).isEmpty();
    }

    // The same fleet, but two destroyers come along. They soak the first round's hits so the carrier can retreat with
    // its infantry; losing about two destroyers is barely worth Sol's action, so the escorted stop pays.
    @Test
    void destroyersEscortTheCarrierPastAStrongFleet() {
        test.game.setRound(3);
        test.units(home, "space", test.nekro, UnitType.Destroyer, 2);
        strongFleetNextDoor();

        TacticalPlan plan = advance().orElseThrow();

        assertThat(moved(plan, UnitType.Destroyer)).isEqualTo(2);
        assertThat(plan.score()).isPositive();
    }

    // Round 3, a dreadnought and a cruiser next door: one destroyer escort turns a worthwhile kill into a group that
    // can retreat, and the escorted carrier is the best move.
    @Test
    void aDestroyerEscortLetsTheCarrierAdvancePastAWeakerFleet() {
        test.game.setRound(3);
        test.units(home, "space", test.nekro, UnitType.Destroyer, 1);
        test.units(enemyPost, "space", test.sol, UnitType.Dreadnought, 1);
        test.units(enemyPost, "space", test.sol, UnitType.Cruiser, 1);

        TacticalPlan plan = TacticalPlanner.best(test.game, test.nekro).orElseThrow();

        assertThat(plan.kind()).isEqualTo(Kind.POSITION);
        assertThat(plan.target()).isEqualTo(neighbour);
        assertThat(moved(plan, UnitType.Destroyer)).isEqualTo(1);
    }

    // Round 1 is where advances happen. A single enemy carrier or destroyer next door barely dents the stop: the kill
    // is
    // small and uncertain, Sol risks its own ship, and round-one damping applies. The carrier advances alone.
    @Test
    void aLoneCarrierAdvancesInRoundOneBesideASingleEnemyCarrier() {
        assertAdvancesAloneBeside(UnitType.Carrier);
    }

    @Test
    void aLoneCarrierAdvancesInRoundOneBesideASingleEnemyDestroyer() {
        assertAdvancesAloneBeside(UnitType.Destroyer);
    }

    private void assertAdvancesAloneBeside(UnitType enemyShip) {
        double alone = advance().orElseThrow().score();
        test.units(enemyPost, "space", test.sol, enemyShip, 1);

        TacticalPlan plan = TacticalPlanner.best(test.game, test.nekro).orElseThrow();

        assertThat(plan.kind()).isEqualTo(Kind.POSITION);
        assertThat(plan.target()).isEqualTo(neighbour);
        assertThat(moved(plan, UnitType.Carrier)).isEqualTo(1);
        assertThat(plan.score()).isCloseTo(alone, within(0.1));
    }

    // The round-one damping is the same one the defence model uses: the same strong fleet costs the same lone carrier
    // far less in round 1 than in round 3.
    @Test
    void roundOneDampsTheRisk() {
        strongFleetNextDoor();
        double roundOne = loneCarrierLoss(0);
        test.game.setRound(3);

        assertThat(roundOne).isLessThan(0.5 * loneCarrierLoss(0));
    }

    // Two of Nekro's dreadnoughts already hold the empty system. Sol's dreadnought and cruiser cannot contend with
    // them,
    // so the carrier joining them is scored as if Sol were not there.
    @Test
    void backupTheEnemyCannotContendWithRemovesTheRisk() {
        test.game.setRound(3);
        double alone = advance().orElseThrow().score();
        Tile post = test.game.getTileByPosition(neighbour);
        test.units(post, "space", test.nekro, UnitType.Dreadnought, 2);
        test.units(enemyPost, "space", test.sol, UnitType.Dreadnought, 1);
        test.units(enemyPost, "space", test.sol, UnitType.Cruiser, 1);

        assertThat(advance().orElseThrow().score()).isCloseTo(alone, within(0.01));
    }

    // Escorts lower the expected loss but leave some risk; backup the enemy cannot beat leaves almost none.
    @Test
    void escortsAndBackupLowerTheExpectedLoss() {
        test.game.setRound(3);
        strongFleetNextDoor();

        double lone = loneCarrierLoss(0);
        double escorted = escortedLoss(0);
        double backed = loss(0, groupMoves(UnitType.Dreadnought, 3));

        assertThat(lone).isGreaterThan(1.0);
        assertThat(escorted).isPositive().isLessThan(0.5);
        assertThat(backed).isLessThan(0.01);
    }

    // The attack share is a smooth, increasing function of appetite: no jump where the kill stops paying for an action,
    // and next to nothing when the enemy would lose.
    @Test
    void theAttackShareIsSmoothInAppetite() {
        double previous = DefenseOutlook.attackShare(-3, 3);
        for (double appetite = -2.9; appetite <= 3; appetite += 0.1) {
            double share = DefenseOutlook.attackShare(appetite, 3);
            assertThat(share).isGreaterThanOrEqualTo(previous);
            assertThat(share - previous).isLessThan(0.03);
            previous = share;
        }
        assertThat(DefenseOutlook.attackShare(-10, 3)).isLessThan(1e-3);
    }

    // The escorted carrier beside the strong fleet sits right where Sol's kill stops paying for an action. Shifting the
    // action cost by a quarter either way moves the expected losses a little and never flips the choice between going
    // alone and going with the destroyers.
    @Test
    void smallChangesInTheActionCostDoNotFlipTheEscortDecision() {
        test.game.setRound(3);
        strongFleetNextDoor();
        double escortCost = 2 * 0.3;

        for (double shift : List.of(-0.25, 0.0, 0.25)) {
            assertThat(escortedLoss(shift) + escortCost).isLessThan(loneCarrierLoss(shift));
            assertThat(escortedLoss(shift)).isCloseTo(escortedLoss(0), within(0.15));
        }
    }

    // Expanding leaves the carrier in space beside the fleet too. The planet is still worth taking, but the plan is
    // priced at half the carrier's exposure.
    @Test
    void anExpansionBesideAStrongFleetIsPricedButStillTaken() {
        test.game.setRound(3);
        test.place("26", neighbour);
        double alone = expansion().score();
        strongFleetNextDoor();

        TacticalPlan plan = expansion();

        assertThat(plan.landings()).containsKey("lodor");
        assertThat(plan.score()).isLessThan(alone).isGreaterThan(alone - 2.0);
    }

    // A carrier and two destroyers leave an empty system for the next one, beside three Sol dreadnoughts. The only
    // system to retreat to is the one they came from: if the move empties it, there is no retreat and the group's whole
    // value is at stake; if a destroyer stays behind, the group can fall back on it.
    @Test
    void noRetreatIntoTheOriginTheMoveEmpties() {
        Board board = new Board();

        double emptied = board.loss();
        board.test.units(board.origin, "space", board.test.nekro, UnitType.Destroyer, 1);
        double held = board.loss();

        assertThat(emptied).isGreaterThan(held + 1.0);
    }

    // The destroyer stays behind, but three more Sol dreadnoughts sit next to it: falling back there only gets the
    // group
    // caught by a stronger force, so it counts as no retreat.
    @Test
    void noRetreatIntoASystemTheAttackerHitsHarder() {
        Board board = new Board();
        board.test.units(board.origin, "space", board.test.nekro, UnitType.Destroyer, 1);
        double held = board.loss();

        board.test.units(board.flank, "space", board.test.sol, UnitType.Dreadnought, 3);

        assertThat(board.loss()).isGreaterThan(held + 1.0);
    }

    private static final class Board {
        private final AiTestGame test = new AiTestGame();
        private final Tile origin;
        private final Tile target;
        private final Tile flank;

        Board() {
            test.game.setRound(3);
            String originPosition = AiTestGame.HOME;
            String targetPosition = AiTestGame.neighbourOf(originPosition);
            List<String> nearOrigin = MapPositionMapper.getAdjacentTilePositions(originPosition);
            List<String> nearTarget = MapPositionMapper.getAdjacentTilePositions(targetPosition);
            String enemyPosition = nearTarget.stream()
                    .filter(position ->
                            !"x".equals(position) && !originPosition.equals(position) && !nearOrigin.contains(position))
                    .findFirst()
                    .orElseThrow();
            String flankPosition = nearOrigin.stream()
                    .filter(position ->
                            !"x".equals(position) && !targetPosition.equals(position) && !nearTarget.contains(position))
                    .findFirst()
                    .orElseThrow();
            origin = test.place("47", originPosition);
            target = test.place("48", targetPosition);
            Tile enemy = test.place("49", enemyPosition);
            flank = test.place("50", flankPosition);
            test.units(origin, "space", test.nekro, UnitType.Carrier, 1);
            test.units(origin, "space", test.nekro, UnitType.Destroyer, 2);
            test.units(origin, "space", test.nekro, UnitType.Infantry, 2);
            test.units(enemy, "space", test.sol, UnitType.Dreadnought, 3);
        }

        double loss() {
            List<UnitMove> moves = List.of(
                    new UnitMove(origin.getPosition(), "space", UnitType.Carrier, 1),
                    new UnitMove(origin.getPosition(), "space", UnitType.Destroyer, 2),
                    new UnitMove(origin.getPosition(), "space", UnitType.Infantry, 2));
            DefenseOutlook defense = new DefenseOutlook(test.game, test.nekro);
            Defenders group = defense.defendersAt(target).arriving(moves);
            return new Exposure(test.game, test.nekro, defense).expectedLoss(target, group, moves);
        }
    }

    private double loneCarrierLoss(double extraActionCost) {
        return loss(extraActionCost, groupMoves(UnitType.Destroyer, 0));
    }

    private double escortedLoss(double extraActionCost) {
        return loss(extraActionCost, groupMoves(UnitType.Destroyer, 2));
    }

    private double loss(double extraActionCost, List<UnitMove> moves) {
        Tile post = test.game.getTileByPosition(neighbour);
        DefenseOutlook defense = new DefenseOutlook(test.game, test.nekro);
        Defenders group = defense.defendersAt(post).arriving(moves);
        return new Exposure(test.game, test.nekro, defense, extraActionCost).expectedLoss(post, group, moves);
    }

    private static List<UnitMove> groupMoves(UnitType escort, int escorts) {
        List<UnitMove> moves = new ArrayList<>(List.of(
                new UnitMove(AiTestGame.HOME, "space", UnitType.Carrier, 1),
                new UnitMove(AiTestGame.HOME, "mordaiii", UnitType.Infantry, 2)));
        if (escorts > 0) moves.add(new UnitMove(AiTestGame.HOME, "space", escort, escorts));
        return moves;
    }

    private TacticalPlan expansion() {
        return TacticalPlanner.forTarget(test.game, test.nekro, neighbour)
                .filter(plan -> plan.kind() == Kind.EXPAND)
                .orElseThrow();
    }

    private void strongFleetNextDoor() {
        test.units(enemyPost, "space", test.sol, UnitType.Dreadnought, 2);
        test.units(enemyPost, "space", test.sol, UnitType.Cruiser, 2);
    }

    private Optional<TacticalPlan> advance() {
        return TacticalPlanner.forTarget(test.game, test.nekro, neighbour).filter(plan -> plan.kind() == Kind.POSITION);
    }

    private static int moved(TacticalPlan plan, UnitType type) {
        return plan.moves().stream()
                .filter(move -> move.type() == type)
                .mapToInt(UnitMove::count)
                .sum();
    }

    private String twoSystemsFromHome() {
        List<String> nearHome = MapPositionMapper.getAdjacentTilePositions(AiTestGame.HOME);
        return MapPositionMapper.getAdjacentTilePositions(neighbour).stream()
                .filter(position ->
                        !"x".equals(position) && !AiTestGame.HOME.equals(position) && !nearHome.contains(position))
                .findFirst()
                .orElseThrow();
    }

    private String besideNeighbourAwayFromHome() {
        List<String> nearHome = MapPositionMapper.getAdjacentTilePositions(AiTestGame.HOME);
        String goal = twoSystemsFromHome();
        return MapPositionMapper.getAdjacentTilePositions(neighbour).stream()
                .filter(position -> !"x".equals(position)
                        && !AiTestGame.HOME.equals(position)
                        && !nearHome.contains(position)
                        && !goal.equals(position))
                .findFirst()
                .orElseThrow();
    }
}
