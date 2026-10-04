package ti4.service.leader.agent;

import java.util.Optional;
import ti4.game.Game;
import ti4.game.Player;

public abstract class TargetedAgent implements AgentModule<Player> {

    @Override
    public Optional<Player> decode(Game game, Player user, String payload) {
        return AgentTargets.player(game, payload);
    }
}
