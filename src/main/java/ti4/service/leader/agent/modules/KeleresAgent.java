package ti4.service.leader.agent.modules;

import java.util.Optional;
import ti4.game.Game;
import ti4.game.Player;
import ti4.service.leader.agent.AgentModule;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentTargets;
import ti4.service.leader.agent.AgentUse;

public final class KeleresAgent extends AgentModule<Player> {

    static final String ID = "keleresagent";
    static final String TARGET_KEY = "keleresAgentTarget";

    public KeleresAgent() {
        super(ID, "Xander Alexin Victori III, the Keleres");
    }

    @Override
    public Optional<Player> decode(Game game, Player user, String payload) {
        return AgentTargets.playerOrUser(game, user, payload);
    }

    @Override
    public String exhaustAnnouncement(AgentUse<Player> use) {
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
