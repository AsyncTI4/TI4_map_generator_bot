package ti4.spring.service.statistics.matchmaking.queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import de.gesundkrank.jskills.Rating;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MatchQualityGateTest {

    @Test
    void thresholdStartsAtBase() {
        assertThat(MatchQualityGate.currentThreshold(Duration.ZERO)).isCloseTo(0.5, within(1e-9));
    }

    @Test
    void thresholdRelaxesPerTwoHourWindow() {
        assertThat(MatchQualityGate.currentThreshold(Duration.ofHours(2))).isCloseTo(0.45, within(1e-9));
        assertThat(MatchQualityGate.currentThreshold(Duration.ofHours(3))).isCloseTo(0.45, within(1e-9));
        assertThat(MatchQualityGate.currentThreshold(Duration.ofHours(4))).isCloseTo(0.40, within(1e-9));
    }

    @Test
    void thresholdNeverGoesNegative() {
        assertThat(MatchQualityGate.currentThreshold(Duration.ofHours(100))).isEqualTo(0.0);
    }

    @Test
    void wouldBlockComparesAgainstRelaxedThreshold() {
        assertThat(MatchQualityGate.wouldBlock(0.48, Duration.ZERO)).isTrue();
        assertThat(MatchQualityGate.wouldBlock(0.48, Duration.ofHours(2))).isFalse();
    }

    @Test
    void maxQueueWaitPicksLongestMemberWait() {
        MatchmakingQueueMember shortWaiter = member("a");
        MatchmakingQueueMember longWaiter = member("b");
        Map<MatchmakingQueueMember, PlayerMatchmakingData> data = Map.of(
                shortWaiter, playerData("a", Duration.ofHours(1)),
                longWaiter, playerData("b", Duration.ofHours(3)));

        Duration maxWait = MatchQualityGate.maxQueueWait(List.of(shortWaiter, longWaiter), data);

        assertThat(maxWait).isEqualTo(Duration.ofHours(3));
    }

    private static MatchmakingQueueMember member(String userId) {
        MatchmakingQueueMember member = new MatchmakingQueueMember();
        member.setUserId(userId);
        return member;
    }

    private static PlayerMatchmakingData playerData(String userId, Duration queueWait) {
        return new PlayerMatchmakingData(
                userId, List.of(), List.of(), new Rating(25, 1.5), Set.of(), 0, Set.of(), queueWait, false, List.of());
    }
}
