package ti4.service.leader.agent.modules.homebrew.goose;

import java.util.Optional;
import ti4.game.Game;
import ti4.game.Player;
import ti4.service.leader.agent.AgentModule;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentUse;

public final class JotunnAgent extends AgentModule<String> {

    public JotunnAgent() {
        super("jotunnagent", "Örnsteinn, the Jotunn");
    }

    @Override
    public Optional<String> decode(Game game, Player user, String payload) {
        return Optional.of(payload);
    }

    @Override
    public AgentOutcome resolve(AgentUse<String> use) {
        return AgentOutcome.none();
    }
}
