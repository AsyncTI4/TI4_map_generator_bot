package ti4.ai.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.function.Supplier;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.brain.DecisionMemo;
import ti4.ai.brain.TickMemo;
import ti4.ai.eval.MoveEnablers.Enabler;
import ti4.ai.scoring.WinOdds;
import ti4.ai.tactical.SkippedSpaceCombat;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.testUtils.BaseTi4Test;

// Tick-long caches whose answer depends on movement reach must keep apart the answers worked out under different
// movement assumptions (Lost Star Chart, Flank Speed, Antimass, fleet limit) and a later activation.
class ShipSpeedAssumptionCacheTest extends BaseTi4Test {

    // Aetherstream adds 1 when an anomaly is adjacent to the target. With Lost Star Chart the alpha target (Lodor) is
    // adjacent to the beta island (Quann), which a dummy token turns into an anomaly; without the card nothing beside
    // the target is an anomaly.
    @Nested
    class ArrivalBonus {

        private final MoveLine line = new MoveLine(2);
        private final Game game = line.test.game;
        private final Player seat = line.test.nekro;
        private final Tile target = line.swap(1, "26");

        {
            Tile island = line.test.place("25", line.islandPosition());
            island.addToken("token_anomalydummy.png", "space");
            seat.addTech("as");
        }

        private <T> T chart(Supplier<T> evaluation) {
            return MoveEnablers.assuming(seat, Enabler.LOST_STAR_CHART, evaluation);
        }

        private int bonus() {
            return ShipSpeed.arrivalBonus(game, seat, target);
        }

        private void activate(Tile system) {
            line.test.isActive(seat, "action");
            game.setActiveSystem(system.getPosition());
            game.setStoredValue("currentActionSummary" + seat.getFaction(), " Activated " + system.getPosition());
        }

        @Test
        void theFixtureOnlyGetsTheBonusThroughTheChart() {
            assertThat(bonus()).isZero();
            assertThat(chart(this::bonus)).isEqualTo(1);
        }

        @Test
        void anAssumedChartIsNotServedThePlainAnswer() {
            try (TickMemo.Open tick = TickMemo.open(game)) {
                assertThat(bonus()).isZero();
                assertThat(chart(this::bonus)).isEqualTo(1);
            }
        }

        @Test
        void thePlainAnswerIsNotServedTheAssumedChart() {
            try (TickMemo.Open tick = TickMemo.open(game)) {
                assertThat(chart(this::bonus)).isEqualTo(1);
                assertThat(bonus()).isZero();
            }
        }

        // A later activation drops the chart, so its answer must not reuse the one worked out with the chart active.
        @Test
        void aLaterActivationDoesNotReuseTheChartedAnswer() {
            try (TickMemo.Open tick = TickMemo.open(game)) {
                assertThat(chart(this::bonus)).isEqualTo(1);
                assertThat(chart(() -> MoveEnablers.laterActivation(this::bonus)))
                        .isZero();
            }
        }

        // The seat plays Lost Star Chart during the tick: its own activation now reaches the island.
        @Test
        void aChartPlayedDuringTheTickIsSeen() {
            try (TickMemo.Open tick = TickMemo.open(game)) {
                assertThat(bonus()).isZero();

                activate(target);
                game.setStoredValue(MoveEnablers.LOST_STAR_CHART_PLAYED, seat.getFaction());
                assertThat(bonus()).isEqualTo(1);
            }
        }
    }

    // WinOdds is kept for the tick; every movement assumption gets its own entry instead of reusing the plain one.
    @Nested
    class WinOddsAssumptions {

        private final AiTestGame test = new AiTestGame();
        private final Player rival = test.addSeat("200000000000000002", "sardakk", "red");

        {
            test.game.setVp(10);
            test.game.setRound(3);
        }

        private WinOdds.Odds asked() {
            return DecidingSeat.of(test.nekro, () -> WinOdds.of(test.game, test.nekro, rival));
        }

        private int kept() {
            Map<?, ?> memo = TickMemo.mapFor(test.game, "win.odds");
            return memo.size();
        }

        @Test
        void eachMovementAssumptionWorksTheOddsOutAgain() {
            try (TickMemo.Open tick = TickMemo.open(test.game)) {
                asked();
                assertThat(kept()).isEqualTo(1);

                MoveBoost.withOneMore(rival, this::asked);
                assertThat(kept()).isEqualTo(2);

                FleetLimit.assumingUnlimited(rival, this::asked);
                assertThat(kept()).isEqualTo(3);

                MovementGraph.withAntimassDeflectors(this::asked);
                assertThat(kept()).isEqualTo(4);

                MoveEnablers.assuming(rival, Enabler.LOST_STAR_CHART, this::asked);
                assertThat(kept()).isEqualTo(5);

                SkippedSpaceCombat.assuming(rival, this::asked);
                assertThat(kept()).isEqualTo(6);
            }
        }

        // WinOdds.of is also kept for the decision; an assumption inside the same decision must not be served the
        // plain answer before it reaches the tick memo.
        @Test
        void oneDecisionWorksTheAssumedOddsOutAgain() {
            try (TickMemo.Open tick = TickMemo.open(test.game);
                    DecisionMemo.Open decision = DecisionMemo.open()) {
                WinOdds.Odds plain = asked();
                WinOdds.Odds boosted = MoveBoost.withOneMore(rival, this::asked);

                assertThat(boosted).isNotSameAs(plain);
                assertThat(kept()).isEqualTo(2);
                assertThat(asked()).isSameAs(plain);
            }
        }

        @Test
        void thePlainOddsAreStillReusedAfterAnAssumption() {
            try (TickMemo.Open tick = TickMemo.open(test.game)) {
                WinOdds.Odds plain = asked();
                MoveBoost.withOneMore(rival, this::asked);

                assertThat(asked()).isSameAs(plain);
                assertThat(kept()).isEqualTo(2);
            }
        }
    }
}
