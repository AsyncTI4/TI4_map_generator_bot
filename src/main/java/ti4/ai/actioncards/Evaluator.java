package ti4.ai.actioncards;

import java.util.Optional;
import ti4.game.Game;
import ti4.game.Player;

@FunctionalInterface
interface Evaluator {

    @FunctionalInterface
    interface AtMoment {

        Optional<Plan> plan(Game game, Player seat, PlayMoment moment);
    }

    Optional<Plan> plan(Game game, Player seat);

    default Optional<Plan> plan(Game game, Player seat, PlayMoment moment) {
        return plan(game, seat);
    }

    static Evaluator atMoment(AtMoment evaluator) {
        return new Evaluator() {
            @Override
            public Optional<Plan> plan(Game game, Player seat) {
                return evaluator.plan(game, seat, PlayMoment.IN_TURN);
            }

            @Override
            public Optional<Plan> plan(Game game, Player seat, PlayMoment moment) {
                return evaluator.plan(game, seat, moment);
            }
        };
    }
}
