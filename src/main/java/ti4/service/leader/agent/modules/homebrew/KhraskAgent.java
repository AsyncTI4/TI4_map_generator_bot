package ti4.service.leader.agent.modules.homebrew;

import net.dv8tion.jda.api.components.buttons.Button;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.buttons.ids.AgentButtonIds;
import ti4.game.Player;
import ti4.service.emoji.FactionEmojis;

public final class KhraskAgent extends SpendModifierAgent {

    static final String ID = "khraskagent";

    public KhraskAgent() {
        super(ID, "Udosh B'rtul, the Khrask");
    }

    public static Button button(Player spender) {
        return Buttons.gray(AgentButtonIds.format(ID, spender.getFaction()), "Use Khrask Agent", FactionEmojis.khrask);
    }

    @Override
    String spentThingEffect() {
        return "to spend 1 non-home planet's resources as additional influence";
    }
}
