package ti4.service.leader.agent;

import java.util.Optional;
import ti4.game.Game;
import ti4.game.Player;

public abstract class UntargetedAgent extends AgentModule<Player> {

    protected UntargetedAgent(String agentId, String displayName) {
        super(agentId, displayName);
    }

    @Override
    public final Optional<Player> decode(Game game, Player user, String payload) {
        return Optional.of(user);
    }
}
