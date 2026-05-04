package ti4.contest.replay.house.naalu;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import ti4.contest.replay.core.CombatReplayHouse;
import ti4.contest.replay.service.CombatReplayHouseScoringContext;
import ti4.contest.replay.service.CombatReplayHouseScoringContext.HousePrediction;
import ti4.contest.replay.service.CombatReplayHouseScoringRule;

@Service
@Order(10)
public class CombatReplayNaaluScoringRule implements CombatReplayHouseScoringRule {

    private static final int GIFT_POINTS_PER_CORRECT_PREDICTION = 4;

    @Override
    public void apply(CombatReplayHouseScoringContext context) {
        int points = 0;
        for (HousePrediction prediction : context.predictions()) {
            if (context.houseForUser(prediction.discordUserId()) == CombatReplayHouse.NAALU && prediction.correct()) {
                points += GIFT_POINTS_PER_CORRECT_PREDICTION;
            }
        }
        context.addAbilitySummary(CombatReplayHouse.NAALU, "Gift of Prophecy", points, true);
    }
}
