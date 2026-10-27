package ti4.service.leader.agent.modules;

import java.util.List;
import net.dv8tion.jda.api.components.buttons.Button;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.thundersedge.TeHelperAgents;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentOutcome.Message;
import ti4.service.leader.agent.AgentUse;
import ti4.service.leader.agent.UntargetedAgent;

public final class RalNelAgent extends UntargetedAgent {

    static final String ID = "ralnelagent";

    public RalNelAgent() {
        super(ID, "Kan Kip Rel, the Ral Nel");
    }

    @Override
    public AgentOutcome resolve(AgentUse<Player> use) {
        Game game = use.game();
        Player user = use.user();
        List<Button> drawnCards = TeHelperAgents.drawRalNelAgentCards(game, user);
        return AgentOutcome.of(Message.privatelyTo(user, TeHelperAgents.ralNelAgentPrompt(game, user), drawnCards));
    }
}
