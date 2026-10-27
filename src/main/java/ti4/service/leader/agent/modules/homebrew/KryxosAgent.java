package ti4.service.leader.agent.modules.homebrew;

import ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.kryxos.KryxosLeadersHandler;
import ti4.game.Player;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentOutcome.Message;
import ti4.service.leader.agent.AgentUse;
import ti4.service.leader.agent.TargetedAgent;

public final class KryxosAgent extends TargetedAgent {

    static final String ID = "kryxosagent";

    public KryxosAgent() {
        super(ID, "Dravok Veyl, the Kryxos");
    }

    @Override
    public AgentOutcome resolve(AgentUse<Player> use) {
        Player target = use.payload();
        return AgentOutcome.offer(
                Message.addressedTo(
                        target,
                        ", please choose the non-faction technology to return for " + use.agentNameFor(target) + ".",
                        KryxosLeadersHandler.agentReturnTechButtons(target)),
                use.noteToUser(
                        target, " has no non-faction technology available to return for " + use.agentName() + "."));
    }
}
