package ti4.ai.yin;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import ti4.ai.expedition.BreakthroughValue;
import ti4.testUtils.BaseTi4Test;

// Yin Ascendant grants an alliance ability of an unused faction when it is gained and each time the Yin scores a
// public objective: about one more per round left, 1.5 trade goods each.
class YinAscendantTest extends BaseTi4Test {

    @Test
    void isWorthAnAllianceNowAndOnePerRoundLeft() {
        YinTestBoard board = new YinTestBoard();
        board.yin.addBreakthrough(YinAscendant.BREAKTHROUGH);
        double value = YinAscendant.value(board.test.game, board.yin);

        assertThat(value).isGreaterThanOrEqualTo(2 * YinAscendant.PER_ALLIANCE);
        assertThat(BreakthroughValue.of(board.test.game, board.yin)).isGreaterThanOrEqualTo(value);
    }

    @Test
    void isWorthNothingOnceUnlocked() {
        YinTestBoard board = new YinTestBoard();
        board.yin.addBreakthrough(YinAscendant.BREAKTHROUGH);
        board.yin.setBreakthroughUnlocked(YinAscendant.BREAKTHROUGH, true);

        assertThat(YinAscendant.value(board.test.game, board.yin)).isZero();
    }
}
