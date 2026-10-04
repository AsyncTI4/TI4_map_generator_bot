package ti4.service.leader.agent.modules;

import net.dv8tion.jda.api.components.buttons.Button;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.buttons.ids.AgentButtonIds;
import ti4.game.Player;
import ti4.service.emoji.FactionEmojis;
import ti4.service.leader.agent.AgentNames;

public final class KhraskAgent extends SpendModifierAgent {

    public static final String ID = "khraskagent";

    static String buttonId(Player owner, Player spender) {
        return AgentButtonIds.formatOwned(owner, ID, spender.getFaction());
    }

    public static Button offer(Player owner) {
        return Buttons.gray(
                buttonId(owner, owner), AgentNames.offerVerb(owner, ID) + "Khrask Agent", FactionEmojis.khrask);
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
