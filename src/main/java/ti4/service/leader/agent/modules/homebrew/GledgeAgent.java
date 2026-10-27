package ti4.service.leader.agent.modules.homebrew;

import ti4.discord.interactions.buttons.ids.AgentButtonIds;
import ti4.game.Player;

public final class GledgeAgent extends SpendModifierAgent {

    static final String ID = "gledgeagent";

    public static String buttonId(Player spender) {
        return AgentButtonIds.format(ID, spender.getFaction());
    }

    @Override
    public String agentId() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Durran, the Gledge";
    }

    @Override
    String spentThingEffect() {
        return "for +3 PRODUCTION value";
    }
}
