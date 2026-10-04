package ti4.service.leader.agent.modules;

import java.util.Optional;
import ti4.discord.interactions.buttons.ids.AgentButtonIds;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.service.leader.agent.AgentModule;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentOutcome.Message;
import ti4.service.leader.agent.AgentUse;

public final class KaloraAgent implements AgentModule<Player> {

    public static final String ID = "kaloraagent";

    public static String buttonId(Player owner) {
        return AgentButtonIds.formatOwned(owner, ID);
    }

    @Override
    public String agentId() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Valzor, the Kalora";
    }

    @Override
    public Optional<Player> decode(Game game, Player user, String payload) {
        return Optional.of(user);
    }

    @Override
    public AgentOutcome resolve(AgentUse<Player> use) {
        Player user = use.user();
        return AgentOutcome.of(Message.withButtons(
                user,
                user.getRepresentationUnfogged() + ", please use the buttons to gain 1 command token.",
                ButtonHelper.getGainCCButtons(user)));
    }
}
