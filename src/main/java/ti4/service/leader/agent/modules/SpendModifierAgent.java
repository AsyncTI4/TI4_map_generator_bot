package ti4.service.leader.agent.modules;

import ti4.game.Player;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentUse;
import ti4.service.leader.agent.TargetedAgent;

public abstract class SpendModifierAgent extends TargetedAgent {

    protected abstract String spentThingEffect();

    @Override
    public String exhaustAnnouncement(AgentUse<Player> use) {
        return use.user().getRepresentation() + " has exhausted " + use.agentName() + " for use on "
                + use.payload().getRepresentationNoPing() + ".";
    }

    @Override
    public AgentOutcome resolve(AgentUse<Player> use) {
        use.payload().addSpentThing("Exhausted " + use.agentName() + ", " + spentThingEffect() + ".");
        return AgentOutcome.none();
    }
}
