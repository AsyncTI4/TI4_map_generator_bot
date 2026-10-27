package ti4.service.leader.agent.modules.homebrew;

import ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.ta.TaLeadersHandler;
import ti4.game.Player;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentOutcome.Message;
import ti4.service.leader.agent.AgentUse;
import ti4.service.leader.agent.TargetedAgent;

public final class TaAgent extends TargetedAgent {

    static final String ID = "taagent";

    public TaAgent() {
        super(ID, "Len, the Ta");
    }

    @Override
    public AgentOutcome resolve(AgentUse<Player> use) {
        Player target = use.payload();
        return AgentOutcome.offer(
                Message.addressedTo(
                                target,
                                ", choose a planet for " + use.agentNameFor(target) + ".",
                                TaLeadersHandler.agentPlanetButtons(use.game(), target))
                        .withPagedButtons(),
                Message.addressedTo(target, " has no explorable planets for " + use.agentNameFor(target) + "."));
    }
}
