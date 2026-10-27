package ti4.service.leader.agent.modules.homebrew;

import java.util.Optional;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.bluereverie.SarcosaAgentHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.service.leader.agent.AgentModule;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentTargets;
import ti4.service.leader.agent.AgentUse;

public final class SarcosaAgent extends AgentModule<Player> {

    static final String ID = "sarcosaagent";

    public SarcosaAgent() {
        super(ID, "Drift-Yol Khas, the Sarcosa");
    }

    @Override
    public Optional<Player> decode(Game game, Player user, String payload) {
        return AgentTargets.playerOrUser(game, user, payload);
    }

    @Override
    public AgentOutcome resolve(AgentUse<Player> use) {
        Player target = use.payload();
        return AgentOutcome.offer(
                target,
                ", choose a system in or adjacent to one containing your units.",
                SarcosaAgentHandler.agentSystemButtons(use.game(), target),
                " has no eligible systems.");
    }
}
