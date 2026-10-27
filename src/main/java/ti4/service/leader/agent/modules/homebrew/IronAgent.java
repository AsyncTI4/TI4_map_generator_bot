package ti4.service.leader.agent.modules.homebrew;

import ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.iron.IronLeadersHandler;
import ti4.game.Player;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentOutcome.Message;
import ti4.service.leader.agent.AgentUse;
import ti4.service.leader.agent.TargetedAgent;

public final class IronAgent extends TargetedAgent {

    static final String ID = "ironagent";

    public IronAgent() {
        super(ID, "Shipwright Kastel, the Iron");
    }

    @Override
    public AgentOutcome resolve(AgentUse<Player> use) {
        Player target = use.payload();
        return AgentOutcome.offer(
                Message.addressedTo(
                                target,
                                IronLeadersHandler.PRODUCTION_SYSTEM_PROMPT,
                                IronLeadersHandler.agentProductionSystemButtons(use.game(), target))
                        .withPagedButtons(),
                Message.addressedTo(target, " has no system containing one of their units with PRODUCTION."));
    }
}
