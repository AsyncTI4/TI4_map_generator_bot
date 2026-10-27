package ti4.service.leader.agent.modules;

import java.util.Optional;
import ti4.game.Game;
import ti4.game.Player;
import ti4.service.leader.agent.AgentModule;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentOutcome.Message;
import ti4.service.leader.agent.AgentUse;

public final class ObsidianAgent extends AgentModule<Player> {

    static final String ID = "obsidianagent";

    public ObsidianAgent() {
        super(ID, "Vos Hollow, the Obsidian");
    }

    @Override
    public Optional<Player> decode(Game game, Player user, String payload) {
        return Optional.of(user);
    }

    @Override
    public AgentOutcome resolve(AgentUse<Player> use) {
        return AgentOutcome.of(Message.to(
                use.user(),
                "Just tell the relevant player to destroy the unit with the assign hits button available under the"
                        + " picture of the combat."));
    }
}
