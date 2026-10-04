package ti4.service.leader.agent.modules;

import java.util.Optional;
import ti4.discord.interactions.buttons.ids.AgentButtonIds;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.Helper;
import ti4.service.leader.agent.AgentModule;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentUse;

public final class LunariumAgent implements AgentModule<Player> {

    public static final String ID = "lunariumagent";

    public static String buttonId(Player owner) {
        return AgentButtonIds.formatOwned(owner, ID);
    }

    @Override
    public String agentId() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Gu'la Ma, the Lunarium";
    }

    @Override
    public Optional<Player> decode(Game game, Player user, String payload) {
        return Optional.of(user);
    }

    @Override
    public AgentOutcome resolve(AgentUse<Player> use) {
        Player user = use.user();
        user.addSpentThing(ID);
        return AgentOutcome.none().withPressedMessageEdit(Helper.buildSpentThingsMessage(user, use.game(), "res"));
    }
}
