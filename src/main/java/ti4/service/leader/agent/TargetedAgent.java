package ti4.service.leader.agent;

import java.util.Optional;
import ti4.game.Game;
import ti4.game.Player;

public abstract class TargetedAgent extends AgentModule<Player> {

    protected TargetedAgent(String agentId, String displayName) {
        super(agentId, displayName);
    }

    @Override
    public Optional<Player> decode(Game game, Player user, String payload) {
        return AgentTargets.player(game, payload);
    }
}
