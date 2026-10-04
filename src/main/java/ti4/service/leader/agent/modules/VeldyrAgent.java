package ti4.service.leader.agent.modules;

import net.dv8tion.jda.api.components.buttons.Button;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.buttons.ids.AgentButtonIds;
import ti4.game.Player;
import ti4.service.emoji.FactionEmojis;
import ti4.service.leader.agent.AgentNames;

public final class VeldyrAgent extends SpendModifierAgent {

    public static final String ID = "veldyragent";

    static String buttonId(Player owner, Player spender) {
        return AgentButtonIds.formatOwned(owner, ID, spender.getFaction());
    }

    public static Button offer(Player owner) {
        return Buttons.red(
                buttonId(owner, owner), AgentNames.offerVerb(owner, ID) + "Veldyr Agent", FactionEmojis.veldyr);
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
    protected String spentThingEffect() {
        return "to pay with one planets influence instead of resources";
    }
}
