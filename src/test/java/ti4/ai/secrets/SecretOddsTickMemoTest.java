package ti4.ai.secrets;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.brain.TickMemo;
import ti4.ai.eval.DecidingSeat;
import ti4.game.Player;
import ti4.testUtils.BaseTi4Test;

// A rival's readiness for each unseen secret only reads the public board, so within one tick every seat that asks
// about the same rival shares the answer instead of measuring each card again.
class SecretOddsTickMemoTest extends BaseTi4Test {

    private AiTestGame test;
    private Player hacan;
    private Player sardakk;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.aiIsActive("action");
        test.nekroHome();
        hacan = test.addSeat("200000000000000002", "hacan", "yellow");
        sardakk = test.addSeat("300000000000000003", "sardakk", "red");
        hacan.setSecret("fsn");
    }

    private double askedBy(Player decider) {
        return DecidingSeat.of(decider, () -> SecretOdds.chanceReady(test.game, decider, hacan));
    }

    private int kept() {
        Map<?, ?> memo = TickMemo.mapFor(test.game, SecretOdds.TICK_MEMO);
        return memo.size();
    }

    @Test
    void everySeatSharesARivalsReadinessWithinTheTick() {
        try (TickMemo.Open tick = TickMemo.open(test.game)) {
            double mine = askedBy(test.nekro);
            int afterFirst = kept();
            double theirs = askedBy(sardakk);

            assertThat(afterFirst).isPositive();
            assertThat(theirs).isEqualTo(mine);
            assertThat(kept()).isEqualTo(afterFirst);
        }
    }

    @Test
    void outsideATickNothingIsKept() {
        askedBy(test.nekro);

        assertThat(kept()).isZero();
    }
}
