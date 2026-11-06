package ti4.service.leader.agent.modules;

import ti4.game.Player;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentOutcome.Message;
import ti4.service.leader.agent.AgentUse;
import ti4.service.leader.agent.UntargetedAgent;

public final class ObsidianAgent extends UntargetedAgent {

    static final String ID = "obsidianagent";

    public ObsidianAgent() {
        super(ID, "Vos Hollow, the Obsidian");
    }

    @Override
    public AgentOutcome resolve(AgentUse<Player> use) {
        return AgentOutcome.of(Message.plain(
                use.user(),
                "Just tell the relevant player to destroy the unit with the assign hits button available under the"
                        + " picture of the combat."));
    }
}
