package ti4.service.leader.agent.modules;

import java.util.List;
import net.dv8tion.jda.api.components.buttons.Button;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
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
        String prompt = ", please choose the action card you wish to send to another player.";
        if (user.getActionCards().size() > ButtonHelper.getACLimit(game, user)) {
            prompt += "\nNB: This happens __before__ discarding down to hand limit.";
        }
        return AgentOutcome.of(Message.addressedTo(user, prompt, drawnCards).inCardsInfoThread());
    }
}
