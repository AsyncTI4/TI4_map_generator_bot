package ti4.service.leader.agent.modules;

import ti4.game.Player;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentOutcome.Message;
import ti4.service.leader.agent.AgentUse;
import ti4.service.leader.agent.TargetedAgent;

abstract class NextRollAgent extends TargetedAgent {

    NextRollAgent(String agentId, String displayName) {
        super(agentId, displayName);
    }

    @Override
    public AgentOutcome resolve(AgentUse<Player> use) {
        Player target = use.payload();
        use.game().setCurrentReacts(agentId(), target.getFaction());
        return AgentOutcome.of(Message.plain(
                use.user(),
                target.getFactionEmojiOrColor() + " will receive " + use.agentName() + ", on their next roll."));
    }
}
