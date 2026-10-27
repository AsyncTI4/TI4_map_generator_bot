package ti4.service.leader.agent.modules.homebrew;

import net.dv8tion.jda.api.components.buttons.Button;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.buttons.ids.AgentButtonIds;
import ti4.game.Player;
import ti4.service.emoji.FactionEmojis;

public final class VeldyrAgent extends SpendModifierAgent {

    static final String ID = "veldyragent";

    public VeldyrAgent() {
        super(ID, "Solis Morden, the Veldyr");
    }

    public static Button button(Player spender) {
        return Buttons.red(AgentButtonIds.format(ID, spender.getFaction()), "Use Veldyr Agent", FactionEmojis.veldyr);
    }

    @Override
    String spentThingEffect() {
        return "to pay with one planets influence instead of resources";
    }
}
