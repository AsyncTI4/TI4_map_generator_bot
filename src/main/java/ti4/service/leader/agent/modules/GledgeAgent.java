package ti4.service.leader.agent.modules;

import net.dv8tion.jda.api.components.buttons.Button;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.buttons.ids.AgentButtonIds;
import ti4.game.Player;
import ti4.service.emoji.FactionEmojis;
import ti4.service.leader.agent.AgentNames;

public final class GledgeAgent extends SpendModifierAgent {

    public static final String ID = "gledgeagent";

    static String buttonId(Player owner, Player spender) {
        return AgentButtonIds.formatOwned(owner, ID, spender.getFaction());
    }

    public static Button offer(Player owner) {
        return Buttons.red(
                buttonId(owner, owner), AgentNames.offerVerb(owner, ID) + "Gledge Agent", FactionEmojis.gledge);
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
    protected String spentThingEffect() {
        return "for +3 PRODUCTION value";
    }
}
