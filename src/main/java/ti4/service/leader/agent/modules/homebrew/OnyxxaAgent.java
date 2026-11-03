package ti4.service.leader.agent.modules.homebrew;

import ti4.discord.interactions.buttons.handlers.faction.homebrew.wftv.onyxxa.OnyxxaLeaderHandler;
import ti4.game.Player;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentOutcome.Message;
import ti4.service.leader.agent.AgentUse;
import ti4.service.leader.agent.TargetedAgent;

public final class OnyxxaAgent extends TargetedAgent {

    static final String ID = "onyxxaagent";

    public OnyxxaAgent() {
        super(ID, "Innovator Zeth, the Onyxxa");
    }

    @Override
    public AgentOutcome resolve(AgentUse<Player> use) {
        Player target = use.payload();
        return AgentOutcome.offer(
                Message.addressedTo(
                        target,
                        ", please choose the system that you wish to move a ship from.",
                        OnyxxaLeaderHandler.agentMoveShipButtons(use.game(), target)),
                use.noteToUser(target, " has no ships to move."));
    }
}
