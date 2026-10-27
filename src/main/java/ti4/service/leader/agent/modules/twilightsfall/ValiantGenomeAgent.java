package ti4.service.leader.agent.modules.twilightsfall;

import ti4.game.Player;
import ti4.helpers.ButtonHelperActionCards;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentOutcome.Message;
import ti4.service.leader.agent.AgentUse;
import ti4.service.leader.agent.TargetedAgent;

public final class ValiantGenomeAgent extends TargetedAgent {

    static final String ID = "valiantagent";

    public ValiantGenomeAgent() {
        super(ID, "Valiant Genome");
    }

    @Override
    public AgentOutcome resolve(AgentUse<Player> use) {
        Player target = use.payload();
        return AgentOutcome.offer(
                Message.addressedTo(
                        target,
                        ", please choose the recently deceased unit that you wish to use the genome on.",
                        ButtonHelperActionCards.getCourageousOptions(target, use.game(), true, "courageous")),
                Message.addressedTo(target, " has no recently destroyed units to use the genome on."));
    }
}
