package ti4.ai.scoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.stream.DoubleStream;
import org.junit.jupiter.api.Test;

class JointWinThreatTest {

    // The case behind JOINT_WIN_THREAT: five rivals each under the even-odds line, so each one alone looks
    // beaten, yet one of them finishes first about 92% of the time.
    @Test
    void fiveRivalsEachUnderEvenOddsAreLikelyToProduceAWinner() {
        double field = WinOdds.anyOf(DoubleStream.of(0.4, 0.4, 0.4, 0.4, 0.4));

        assertThat(field).isCloseTo(0.922, within(0.001)).isGreaterThan(WinOdds.MAJORITY);
    }

    // With one rival the joint test is the old per-rival test, so a one-on-one race is judged the same way.
    @Test
    void aSingleRivalIsJudgedExactlyAsBefore() {
        assertThat(WinOdds.anyOf(DoubleStream.of(0.4))).isCloseTo(0.4, within(1e-12));
        assertThat(WinOdds.anyOf(DoubleStream.of(0.6))).isCloseTo(0.6, within(1e-12));
    }

    @Test
    void noRivalsMeansNobodyFinishesFirst() {
        assertThat(WinOdds.anyOf(DoubleStream.empty())).isZero();
    }

    @Test
    void aRivalCertainToFinishFirstDecidesTheField() {
        assertThat(WinOdds.anyOf(DoubleStream.of(0.1, 1.0, 0.2))).isCloseTo(1.0, within(1e-12));
    }
}
