package ti4.service.leader.agent.modules.homebrew;

import net.dv8tion.jda.api.components.buttons.Button;
import ti4.discord.interactions.buttons.Buttons;
import ti4.game.Player;
import ti4.service.emoji.FactionEmojis;

public final class GledgeAgent extends SpendModifierAgent {

    static final String ID = "gledgeagent";

    public GledgeAgent() {
        super(ID, "Durran, the Gledge");
    }

    public static Button button(Player spender) {
        return Buttons.red(
                ownedButtonId(spender, ID, spender.getFaction()),
                useLabel(spender, ID, "Gledge Agent"),
                FactionEmojis.gledge);
    }

    @Override
    String spentThingEffect() {
        return "for +3 PRODUCTION value";
    }
}
