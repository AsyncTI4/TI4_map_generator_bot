package ti4.service.leader.agent.modules;

import ti4.discord.interactions.buttons.ids.AgentButtonIds;
import ti4.game.Player;
import ti4.helpers.ActionCardHelper;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentUse;
import ti4.service.leader.agent.TargetedAgent;

public final class MentakAgent extends TargetedAgent {

    public static final String ID = "mentakagent";

    public static String buttonId(Player pillaged) {
        return AgentButtonIds.format(ID, pillaged.getFaction());
    }

    @Override
    public String agentId() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Suffi An, the Mentak";
    }

    @Override
    public AgentOutcome resolve(AgentUse<Player> use) {
        ActionCardHelper.drawActionCards(use.user(), 1);
        ActionCardHelper.drawActionCards(use.payload(), 1);
        return AgentOutcome.none();
    }
}
