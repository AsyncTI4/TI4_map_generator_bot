package ti4.service.leader.agent.modules.homebrew;

import ti4.discord.interactions.buttons.handlers.faction.homebrew.wftv.zephyrion.ZephyrionLeaderHandler;
import ti4.game.Player;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentOutcome.Message;
import ti4.service.leader.agent.AgentUse;
import ti4.service.leader.agent.UntargetedAgent;

public final class ZephyrionAgent extends UntargetedAgent {

    static final String ID = "zephyrionagent";

    public ZephyrionAgent() {
        super(ID, "Rhino the Adventurer, the Zephyrion");
    }

    @Override
    public AgentOutcome resolve(AgentUse<Player> use) {
        Player zephyrion = use.user();
        return AgentOutcome.of(Message.addressedTo(
                zephyrion,
                ", you may use the buttons to select the ship you want to kill.",
                ZephyrionLeaderHandler.agentTargetButtons(use.game(), zephyrion)));
    }
}
