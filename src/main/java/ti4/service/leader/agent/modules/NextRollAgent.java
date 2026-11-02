package ti4.service.leader.agent.modules;

import ti4.game.Player;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentOutcome.Message;
import ti4.service.leader.agent.AgentUse;
import ti4.service.leader.agent.TargetedAgent;

public abstract class NextRollAgent extends TargetedAgent {

    NextRollAgent(String agentId, String displayName) {
        super(agentId, displayName);
    }

    public static AgentOutcome promiseOnNextRoll(AgentUse<Player> use, String storedKey) {
        Player target = use.payload();
        use.game().setCurrentReacts(storedKey, target.getFaction());
        return AgentOutcome.of(Message.plain(
                use.user(),
                target.getFactionEmojiOrColor() + " will receive " + use.agentName() + ", on their next roll."));
    }

    @Override
    public AgentOutcome resolve(AgentUse<Player> use) {
        return promiseOnNextRoll(use, agentId());
    }
}
