package ti4.service.leader.agent.modules.homebrew;

import ti4.game.Player;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentUse;
import ti4.service.leader.agent.SpendAgent;

abstract class SpendModifierAgent extends SpendAgent {

    SpendModifierAgent(String agentId, String displayName) {
        super(agentId, displayName);
    }

    abstract String spentThingEffect();

    @Override
    public AgentOutcome resolve(AgentUse<Player> use) {
        use.payload().addSpentThing("Exhausted " + use.agentName() + ", " + spentThingEffect() + ".");
        return AgentOutcome.none();
    }
}
