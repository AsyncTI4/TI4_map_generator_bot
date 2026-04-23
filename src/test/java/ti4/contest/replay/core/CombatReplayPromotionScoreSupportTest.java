package ti4.contest.replay.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class CombatReplayPromotionScoreSupportTest {

    @Test
    void computePromotionScoreAppliesBlowoutPenalty() {
        assertEquals(2.75, CombatReplayPromotionScoreSupport.computePromotionScore(1.0, 0.0, 3), 0.0001);
    }

    @Test
    void computeLossRatioClampsBetweenZeroAndOne() {
        assertEquals(0.0, CombatReplayPromotionScoreSupport.computeLossRatio(0.0, 5.0), 0.0001);
        assertEquals(0.5, CombatReplayPromotionScoreSupport.computeLossRatio(20.0, 10.0), 0.0001);
        assertEquals(1.0, CombatReplayPromotionScoreSupport.computeLossRatio(20.0, -5.0), 0.0001);
    }
}
