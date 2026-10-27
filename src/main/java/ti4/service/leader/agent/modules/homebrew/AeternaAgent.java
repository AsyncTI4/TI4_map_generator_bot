package ti4.service.leader.agent.modules.homebrew;

import ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.aeterna.AeternaLeadersHandler;
import ti4.game.Player;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentUse;
import ti4.service.leader.agent.TargetedAgent;

public final class AeternaAgent extends TargetedAgent {

    static final String ID = "aeternaagent";

    public AeternaAgent() {
        super(ID, "Morwen Deyth, the Aeterna");
    }

    @Override
    public AgentOutcome resolve(AgentUse<Player> use) {
        Player target = use.payload();
        return AgentOutcome.offer(
                target,
                ", please choose a non-fighter ship to destroy for " + use.agentName() + ".",
                AeternaLeadersHandler.agentShipButtons(use.game(), target),
                " has no eligible non-fighter ships.");
    }
}
