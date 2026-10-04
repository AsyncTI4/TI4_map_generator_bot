package ti4.service.leader.agent.modules;

import ti4.discord.interactions.buttons.ids.AgentButtonIds;
import ti4.game.Player;

public final class KhraskAgent extends SpendModifierAgent {

    public static final String ID = "khraskagent";

    public static String buttonId(Player spender) {
        return AgentButtonIds.format(ID, spender.getFaction());
    }

    @Override
    public String agentId() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Udosh B'rtul, the Khrask";
    }

    @Override
    protected String spentThingEffect() {
        return "to spend 1 non-home planet's resources as additional influence";
    }
}
