package ti4.ai.agenda;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class BallotPlannerTest {

    // The planner casts the fewest votes within 0.3 TG of its best ballot, but never a ballot that falls below the gain
    // over abstaining the best one had to clear: here 1 vote is near enough to the best but under the floor, so the
    // planner casts 2.
    @Test
    void theFewestVotesNearTheBestStillClearTheFloor() {
        BallotPlanner.Option one = new BallotPlanner.Option("for", 1, 0.7);
        BallotPlanner.Option two = new BallotPlanner.Option("for", 2, 0.9);
        BallotPlanner.Option four = new BallotPlanner.Option("for", 4, 1.0);
        List<BallotPlanner.Option> valued = List.of(one, two, four, new BallotPlanner.Option("against", 1, 0.95));

        assertThat(BallotPlanner.fewestNearBest(valued, four, 0.8)).isEqualTo(two);
        assertThat(BallotPlanner.fewestNearBest(valued, four, Double.NEGATIVE_INFINITY))
                .isEqualTo(one);
    }
}
