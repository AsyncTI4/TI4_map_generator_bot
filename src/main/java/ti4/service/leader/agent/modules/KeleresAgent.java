package ti4.service.leader.agent.modules;

import ti4.game.Player;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentUse;
import ti4.service.leader.agent.SelfOrTargetedAgent;

public final class KeleresAgent extends SelfOrTargetedAgent {

    static final String ID = "keleresagent";
    static final String TARGET_KEY = "keleresAgentTarget";

    public KeleresAgent() {
        super(ID, "Xander Alexin Victori III, the Keleres");
    }

    @Override
    public String exhaustAnnouncementAfterName(AgentUse<Player> use) {
        return " has exhausted " + use.agentName() + " to target "
                + use.payload().getRepresentationNoPing() + ".";
    }

    @Override
    public AgentOutcome resolve(AgentUse<Player> use) {
        Player target = use.payload();
        if (target != use.user()) {
            use.game().setStoredValue(TARGET_KEY, target.getFaction());
        }
        return AgentOutcome.none();
    }
}
