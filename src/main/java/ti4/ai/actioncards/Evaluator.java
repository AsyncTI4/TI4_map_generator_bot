package ti4.ai.actioncards;

import java.util.Optional;
import ti4.game.Game;
import ti4.game.Player;

@FunctionalInterface
interface Evaluator {

    Optional<Plan> plan(Game game, Player seat);
}
