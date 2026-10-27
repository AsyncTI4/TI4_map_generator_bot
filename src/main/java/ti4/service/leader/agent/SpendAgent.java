package ti4.service.leader.agent;

import ti4.game.Player;

public abstract class SpendAgent extends TargetedAgent {

    protected SpendAgent(String agentId, String displayName) {
        super(agentId, displayName);
    }

    @Override
    public String exhaustAnnouncement(AgentUse<Player> use) {
        return use.user().getRepresentation() + " has exhausted " + use.agentName() + " for use on "
                + use.payload().getRepresentationNoPing() + ".";
    }
}
