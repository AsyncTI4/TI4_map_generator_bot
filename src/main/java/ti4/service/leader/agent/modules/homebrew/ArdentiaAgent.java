package ti4.service.leader.agent.modules.homebrew;

import ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.ardentia.ArdentiaLeadersHandler;
import ti4.game.Player;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentOutcome.Message;
import ti4.service.leader.agent.AgentUse;
import ti4.service.leader.agent.TargetedAgent;

public final class ArdentiaAgent extends TargetedAgent {

    static final String ID = "ardentiaagent";

    public ArdentiaAgent() {
        super(ID, "Viren Tolar, the Ardentia");
    }

    @Override
    public AgentOutcome resolve(AgentUse<Player> use) {
        Player target = use.payload();
        return AgentOutcome.of(Message.withButtons(
                target,
                target.getRepresentationUnfogged() + ", please choose how to spend 1 influence.",
                ArdentiaLeadersHandler.agentPaymentButtons(use.game(), target)));
    }
}
