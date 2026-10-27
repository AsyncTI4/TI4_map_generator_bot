package ti4.service.leader.agent.modules.homebrew;

import java.util.List;
import ti4.discord.interactions.buttons.Buttons;
import ti4.game.Player;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentOutcome.Message;
import ti4.service.leader.agent.AgentUse;
import ti4.service.leader.agent.TargetedAgent;

public final class KolumeAgent extends TargetedAgent {

    static final String ID = "kolumeagent";

    public KolumeAgent() {
        super(ID, "Disciple Fran, the Kolume");
    }

    @Override
    public AgentOutcome resolve(AgentUse<Player> use) {
        Player target = use.payload();
        return AgentOutcome.of(Message.addressedTo(
                target,
                ", use buttons to redistribute 1 command token (the bot allows more but " + use.agentNameFor(target)
                        + " is restricted to redistributing 1).",
                List.of(
                        Buttons.green(
                                target.factionButtonChecker() + "redistributeCCButtons", "Redistribute Command Tokens"),
                        Buttons.red(target.factionButtonChecker() + "deleteButtons", "Delete These Buttons"))));
    }
}
