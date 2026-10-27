package ti4.service.leader.agent.modules;

import ti4.game.Player;
import ti4.helpers.ButtonHelperAgents;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentOutcome.Message;
import ti4.service.leader.agent.AgentUse;
import ti4.service.leader.agent.TargetedAgent;

public final class ArborecAgent extends TargetedAgent {

    static final String ID = "arborecagent";

    public ArborecAgent() {
        super(ID, "Letani Ospha, the Arborec");
    }

    @Override
    public AgentOutcome resolve(AgentUse<Player> use) {
        Player replacer = use.payload();
        return AgentOutcome.offer(
                Message.addressedTo(
                        replacer,
                        ", please choose which system to use " + use.agentNameFor(replacer) + " in.",
                        ButtonHelperAgents.getTilesToArboAgent(replacer, use.game())),
                Message.addressedTo(replacer, " has no system with units to replace."));
    }
}
