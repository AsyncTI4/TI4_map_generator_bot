package ti4.service.leader.agent.modules.homebrew;

import ti4.discord.interactions.buttons.ids.AgentButtonIds;
import ti4.game.Player;

public final class VeldyrAgent extends SpendModifierAgent {

    static final String ID = "veldyragent";

    public static String buttonId(Player spender) {
        return AgentButtonIds.format(ID, spender.getFaction());
    }

    @Override
    public String agentId() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Solis Morden, the Veldyr";
    }

    @Override
    String spentThingEffect() {
        return "to pay with one planets influence instead of resources";
    }
}
