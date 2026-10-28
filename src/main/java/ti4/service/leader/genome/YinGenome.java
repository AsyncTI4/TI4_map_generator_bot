package ti4.service.leader.genome;

import ti4.game.Player;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentOutcome.Message;
import ti4.service.leader.agent.AgentUse;
import ti4.service.leader.agent.modules.YinAgent;

public final class YinGenome extends LeaningGenome<YinAgent.Replacement> {

    static final String ID = "yinagent";

    public YinGenome() {
        super(ID, "Splitting Genome", new YinAgent());
    }

    @Override
    public AgentOutcome resolve(AgentUse<YinAgent.Replacement> use) {
        Player owner = use.payload().owner();
        return AgentOutcome.of(Message.addressedTo(
                owner,
                ", use buttons to resolve " + use.agentNameFor(owner) + ".",
                YinAgent.fighterButtons(owner, use.payload().system())));
    }
}
