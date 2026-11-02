package ti4.service.leader.genome.homebrew.tknova;

import ti4.game.Player;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentUse;
import ti4.service.leader.agent.modules.LetnevAgent;
import ti4.service.leader.agent.modules.NextRollAgent;
import ti4.service.leader.genome.LeaningGenome;

public final class AristocraticGenome extends LeaningGenome<Player> {

    public static final String ID = "tknovaaristocraticagent";

    public AristocraticGenome() {
        super(ID, "Aristocratic Genome", new LetnevAgent());
    }

    @Override
    public AgentOutcome resolve(AgentUse<Player> use) {
        return NextRollAgent.promiseOnNextRoll(use, ID);
    }
}
