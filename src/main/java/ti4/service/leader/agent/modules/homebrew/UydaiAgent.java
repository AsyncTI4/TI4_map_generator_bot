package ti4.service.leader.agent.modules.homebrew;

import ti4.game.Player;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentOutcome.Message;
import ti4.service.leader.agent.AgentUse;
import ti4.service.leader.agent.UntargetedAgent;

public final class UydaiAgent extends UntargetedAgent {

    static final String ID = "uydaiagent";

    public UydaiAgent() {
        super(ID, "Garstil, the Uydai");
    }

    @Override
    public AgentOutcome resolve(AgentUse<Player> use) {
        return AgentOutcome.of(Message.to(
                use.user(),
                "This is not automated. The following command allows you to place a card back on the bottom of a"
                        + " deck: `/game place_drawable_card`. Any immediate effects of the already drawn card will"
                        + " need to be undone manually. Use the search function to find a cards ID if unknown."));
    }
}
