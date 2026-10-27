package ti4.service.leader.agent.modules.homebrew;

import ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.vanguard.VanguardLeadersHandler;
import ti4.game.Player;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentOutcome.Message;
import ti4.service.leader.agent.AgentUse;
import ti4.service.leader.agent.TargetedAgent;

public final class VanguardAgent extends TargetedAgent {

    static final String ID = "vanguardagent";

    public VanguardAgent() {
        super(ID, "Shieldmaiden Gabriella, the Vanguard");
    }

    @Override
    public AgentOutcome resolve(AgentUse<Player> use) {
        Player target = use.payload();
        return AgentOutcome.offer(
                Message.addressedTo(
                        target,
                        ", choose a non-home planet containing your ground forces for " + use.agentNameFor(target)
                                + ".",
                        VanguardLeadersHandler.agentPlanetButtons(use.game(), target)),
                Message.addressedTo(target, " has no eligible non-home planet containing their ground forces."));
    }
}
