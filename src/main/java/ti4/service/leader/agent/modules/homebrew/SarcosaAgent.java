package ti4.service.leader.agent.modules.homebrew;

import ti4.discord.interactions.buttons.handlers.faction.homebrew.bluereverie.SarcosaAgentHandler;
import ti4.game.Player;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentOutcome.Message;
import ti4.service.leader.agent.AgentUse;
import ti4.service.leader.agent.SelfOrTargetedAgent;

public final class SarcosaAgent extends SelfOrTargetedAgent {

    static final String ID = "sarcosaagent";

    public SarcosaAgent() {
        super(ID, "Drift-Yol Khas, the Sarcosa");
    }

    @Override
    public AgentOutcome resolve(AgentUse<Player> use) {
        Player target = use.payload();
        return AgentOutcome.offer(
                Message.addressedTo(
                                target,
                                ", choose a system in or adjacent to one containing your units.",
                                SarcosaAgentHandler.agentSystemButtons(use.game(), target))
                        .withPagedButtons(),
                use.noteToUser(target, " has no eligible systems."));
    }
}
