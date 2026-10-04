package ti4.service.leader.agent.modules;

import ti4.discord.interactions.buttons.ids.AgentButtonIds;
import ti4.game.Player;
import ti4.helpers.ActionCardHelper;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentUse;
import ti4.service.leader.agent.TargetedAgent;

public final class CymiaeAgent extends TargetedAgent {

    public static final String ID = "cymiaeagent";

    public static String buttonId(Player owner, Player target) {
        return AgentButtonIds.formatOwned(owner, ID, target.getFaction());
    }

    @Override
    public String agentId() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Skhot Unit X-12, the Cymiae";
    }

    @Override
    public AgentOutcome resolve(AgentUse<Player> use) {
        ActionCardHelper.drawActionCards(use.payload(), 1);
        return AgentOutcome.none();
    }
}
