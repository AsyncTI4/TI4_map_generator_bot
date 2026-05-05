package ti4.contest.replay.house.mentak;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import ti4.contest.replay.core.CombatReplayHouse;
import ti4.contest.replay.service.CombatReplayHouseScoringContext;
import ti4.contest.replay.service.CombatReplayHouseScoringContext.HousePrediction;
import ti4.contest.replay.service.CombatReplayHouseScoringRule;

@Service
@Order(20)
public class CombatReplayMentakScoringRule implements CombatReplayHouseScoringRule {

    private static final int PILLAGE_POINTS_PER_OTHER_DELEGATION_MISS = 4;

    @Override
    public void apply(CombatReplayHouseScoringContext context) {
        int points = 0;
        for (HousePrediction prediction : context.predictions()) {
            CombatReplayHouse house = context.houseForUser(prediction.discordUserId());
            if (house != null && house != CombatReplayHouse.MENTAK && !prediction.correct()) {
                points += PILLAGE_POINTS_PER_OTHER_DELEGATION_MISS;
            }
        }
        context.addAbilitySummary(CombatReplayHouse.MENTAK, "Pillage", points, true);
    }
}
