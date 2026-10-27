package ti4.service.leader.agent.modules.homebrew;

import java.util.Optional;
import ti4.game.Game;
import ti4.game.Player;
import ti4.service.leader.agent.AgentModule;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentOutcome.Message;
import ti4.service.leader.agent.AgentUse;

public final class UydaiAgent extends AgentModule<Player> {

    static final String ID = "uydaiagent";

    public UydaiAgent() {
        super(ID, "Garstil, the Uydai");
    }

    @Override
    public Optional<Player> decode(Game game, Player user, String payload) {
        return Optional.of(user);
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
