package ti4.ai.tactical;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.brain.DecisionMemo;
import ti4.ai.brain.TickMemo;
import ti4.ai.eval.MoveBoost;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.model.MapPositionMapper;
import ti4.testUtils.BaseTi4Test;

// A movement "what if" (here MoveBoost.withOneMore, the Flank Speed assumption) asks a different question about the
// same board. Every cache whose answer depends on how far ships reach must keep the assumed world and the plain one
// apart, in both orders: a plain answer must not be served inside the assumption, nor the assumed one after it.
class MovementAssumptionCacheTest extends BaseTi4Test {

    private static final double CLOSE = 1e-9;

    @AfterEach
    void forgetEnemyIntent() {
        EnemyIntent.forget();
    }

    // --- StyxTake: Sardakk's fleet two moves from Styx (frac2 -> frac3 -> frac4) only reaches it with one more move.

    private static final class StyxBoard {
        final FractureScenario fracture = new FractureScenario();
        final Player sardakk = fracture.board.sardakk;

        StyxBoard() {
            fracture.board.test.game.setPhaseOfGame("action");
            sardakk.setTacticalCC(3);
            sardakk.setFleetCC(12);
            Tile twoFromStyx = fracture.fracture("frac2");
            fracture.board.test.units(twoFromStyx, "space", sardakk, UnitType.Dreadnought, 5);
            fracture.board.test.units(twoFromStyx, "space", sardakk, UnitType.Carrier, 2);
            fracture.board.test.units(twoFromStyx, "space", sardakk, UnitType.Infantry, 4);
        }

        double take() {
            return StyxTake.thisRound(fracture.board.test.game, sardakk);
        }

        double boostedTake() {
            return MoveBoost.withOneMore(sardakk, this::take);
        }

        // Nothing of Sardakk's is in the staging system, so stopping it leaves the take unchanged.
        double takeWithout() {
            return StyxTake.thisRoundWithout(fracture.board.test.game, sardakk, fracture.board.staging);
        }

        double boostedTakeWithout() {
            return MoveBoost.withOneMore(sardakk, this::takeWithout);
        }
    }

    @Test
    void styxTakeKeepsTheBoostedWorldApartWithinADecision() {
        StyxBoard board = new StyxBoard();
        double plain = board.take();
        double boosted = board.boostedTake();
        // The premise: one more move is what puts Styx in reach this round.
        assertThat(plain).isZero();
        assertThat(boosted).isPositive();

        try (DecisionMemo.Open ignored = DecisionMemo.open()) {
            assertThat(board.take()).isZero();
            assertThat(board.boostedTake()).isCloseTo(boosted, within(CLOSE));
        }
        try (DecisionMemo.Open ignored = DecisionMemo.open()) {
            assertThat(board.boostedTake()).isCloseTo(boosted, within(CLOSE));
            assertThat(board.take()).isZero();
        }
    }

    @Test
    void styxTakeWithoutAStoppedSystemKeepsTheBoostedWorldApartWithinADecision() {
        StyxBoard board = new StyxBoard();
        double plain = board.takeWithout();
        double boosted = board.boostedTakeWithout();
        assertThat(plain).isZero();
        assertThat(boosted).isPositive();

        try (DecisionMemo.Open ignored = DecisionMemo.open()) {
            assertThat(board.takeWithout()).isZero();
            assertThat(board.boostedTakeWithout()).isCloseTo(boosted, within(CLOSE));
        }
        try (DecisionMemo.Open ignored = DecisionMemo.open()) {
            assertThat(board.boostedTakeWithout()).isCloseTo(boosted, within(CLOSE));
            assertThat(board.takeWithout()).isZero();
        }
    }

    // --- StyxTake with an assumed skipped space combat: two unescorted carriers beside Styx lose the space combat to
    // its neutral dreadnoughts and destroyer, so only the world that skips space combat lands the infantry.

    @Test
    void styxTakeKeepsAnAssumedSkippedSpaceCombatApartWithinADecision() {
        FractureScenario fracture = new FractureScenario();
        Player sardakk = fracture.board.sardakk;
        fracture.board.test.game.setPhaseOfGame("action");
        sardakk.setTacticalCC(3);
        sardakk.setFleetCC(12);
        Tile besideStyx = fracture.fracture("frac3");
        fracture.board.test.units(besideStyx, "space", sardakk, UnitType.Carrier, 2);
        fracture.board.test.units(besideStyx, "space", sardakk, UnitType.Infantry, 4);
        Supplier<Double> take = () -> StyxTake.thisRound(fracture.board.test.game, sardakk);
        Supplier<Double> skipping = () -> SkippedSpaceCombat.assuming(sardakk, take);

        double plain = take.get();
        double skipped = skipping.get();
        // The premise: skipping the space combat is what makes the landing likely.
        assertThat(skipped).isGreaterThan(plain + 0.1);

        try (DecisionMemo.Open ignored = DecisionMemo.open()) {
            assertThat(take.get()).isCloseTo(plain, within(CLOSE));
            assertThat(skipping.get()).isCloseTo(skipped, within(CLOSE));
        }
        try (DecisionMemo.Open ignored = DecisionMemo.open()) {
            assertThat(skipping.get()).isCloseTo(skipped, within(CLOSE));
            assertThat(take.get()).isCloseTo(plain, within(CLOSE));
        }
    }

    // --- PlanetTake: Sol's fleet two systems from Mecatol Rex (201 -> 101 -> 000), held by one Hacan infantry.

    private static final class MecatolBoard {
        final AiTestGame test = new AiTestGame();

        MecatolBoard() {
            test.game.setVp(10);
            test.game.setRound(4);
            test.aiIsActive("action");
            test.nekroHome();
            Tile rex = test.place("18", "000");
            test.place("46", "101");
            Tile twoAway = test.place("46", "201");
            for (int card = 1; card <= 8; card++) test.game.setScTradeGood(card, 0);
            test.nekro.setTacticalCC(3);
            test.sol.setTacticalCC(3);
            test.sol.setFleetCC(8);
            Player hacan = test.addSeat("300000000000000003", "hacan", "yellow");
            hacan.addPlanet("mr");
            test.units(rex, "mr", hacan, UnitType.Infantry, 1);
            test.units(twoAway, "space", test.sol, UnitType.Dreadnought, 2);
            test.units(twoAway, "space", test.sol, UnitType.Carrier, 1);
            test.units(twoAway, "space", test.sol, UnitType.Infantry, 4);
        }

        double take() {
            return PlanetTake.mecatolThisRound(test.game, test.nekro, test.sol);
        }

        double boostedTake() {
            return MoveBoost.withOneMore(test.sol, this::take);
        }
    }

    @Test
    void mecatolTakeKeepsTheBoostedWorldApartWithinADecision() {
        MecatolBoard board = new MecatolBoard();
        double plain = board.take();
        double boosted = board.boostedTake();
        // The premise: carriers and dreadnoughts move 1, so only the boosted fleet reaches Mecatol Rex this round.
        assertThat(plain).isZero();
        assertThat(boosted).isPositive();

        try (DecisionMemo.Open ignored = DecisionMemo.open()) {
            assertThat(board.take()).isZero();
            assertThat(board.boostedTake()).isCloseTo(boosted, within(CLOSE));
        }
        try (DecisionMemo.Open ignored = DecisionMemo.open()) {
            assertThat(board.boostedTake()).isCloseTo(boosted, within(CLOSE));
            assertThat(board.take()).isZero();
        }
    }

    // --- TacticalStrength.against: a Sardakk carrier group two systems from Nekro's home reaches it now only with
    // one more move, so its reach toward Nekro weighs more under the boost.

    private static final class ReachBoard {
        final DefenseScenario board = new DefenseScenario();

        ReachBoard() {
            Tile far = board.test.place("47", board.beyondStaging());
            board.test.units(far, "space", board.sardakk, UnitType.Carrier, 1);
            board.test.units(far, "space", board.sardakk, UnitType.Cruiser, 2);
            board.test.units(far, "space", board.sardakk, UnitType.Infantry, 4);
        }

        double reach() {
            return TacticalStrength.against(board.test.game, board.sardakk, board.test.nekro, false);
        }

        double boostedReach() {
            return MoveBoost.withOneMore(board.sardakk, this::reach);
        }
    }

    @Test
    void tacticalStrengthKeepsTheBoostedWorldApartWithinATick() {
        ReachBoard board = new ReachBoard();
        double plain = board.reach();
        double boosted = board.boostedReach();
        assertThat(boosted).isGreaterThan(plain);

        try (TickMemo.Open ignored = TickMemo.open(board.board.test.game)) {
            assertThat(board.reach()).isCloseTo(plain, within(CLOSE));
            assertThat(board.boostedReach()).isCloseTo(boosted, within(CLOSE));
        }
        try (TickMemo.Open ignored = TickMemo.open(board.board.test.game)) {
            assertThat(board.boostedReach()).isCloseTo(boosted, within(CLOSE));
            assertThat(board.reach()).isCloseTo(plain, within(CLOSE));
        }
    }

    // --- EnemyIntent: the long-lived cache of a rival's imagined plans. Nekro's carrier (move 1) with infantry reaches
    // the free planets two systems from home only with one more move (the Flank Speed board).

    private static final class IntentBoard {
        final AiTestGame test = new AiTestGame();
        final String farTarget;

        IntentBoard() {
            test.nekro.setTacticalCC(3);
            Tile home = test.nekroHome();
            String neighbour = AiTestGame.neighbourOf(AiTestGame.HOME);
            test.place("47", neighbour);
            farTarget = twoSystemsFromHome(neighbour);
            test.place("27", farTarget);
            test.units(home, "space", test.nekro, UnitType.Carrier, 1);
            test.units(home, "mordaiii", test.nekro, UnitType.Infantry, 3);
        }

        List<String> targets() {
            return EnemyIntent.ranked(test.game, test.nekro).orElseThrow().stream()
                    .map(TacticalPlan::target)
                    .toList();
        }

        List<String> boostedTargets() {
            return MoveBoost.withOneMore(test.nekro, this::targets);
        }

        private static String twoSystemsFromHome(String neighbour) {
            List<String> nearHome = MapPositionMapper.getAdjacentTilePositions(AiTestGame.HOME);
            return MapPositionMapper.getAdjacentTilePositions(neighbour).stream()
                    .filter(position ->
                            !"x".equals(position) && !AiTestGame.HOME.equals(position) && !nearHome.contains(position))
                    .findFirst()
                    .orElseThrow();
        }
    }

    @Test
    void enemyIntentKeepsTheBoostedWorldApart() {
        IntentBoard board = new IntentBoard();
        EnemyIntent.forget();
        List<String> plain = board.targets();
        EnemyIntent.forget();
        List<String> boosted = board.boostedTargets();
        // The premise: only the boosted rival plans the far target.
        assertThat(plain).doesNotContain(board.farTarget);
        assertThat(boosted).contains(board.farTarget);

        // The cache lives across decisions and ticks, keyed by the save; the same save must not hand one world's
        // plans to the other, whichever is asked first.
        EnemyIntent.forget();
        assertThat(board.targets()).isEqualTo(plain);
        assertThat(board.boostedTargets()).isEqualTo(boosted);

        EnemyIntent.forget();
        assertThat(board.boostedTargets()).isEqualTo(boosted);
        assertThat(board.targets()).isEqualTo(plain);
    }
}
