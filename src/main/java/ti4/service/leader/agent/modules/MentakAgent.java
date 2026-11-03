package ti4.service.leader.agent.modules;

import net.dv8tion.jda.api.components.buttons.Button;
import ti4.discord.interactions.buttons.Buttons;
import ti4.game.Player;
import ti4.helpers.ActionCardHelper;
import ti4.service.emoji.FactionEmojis;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentUse;
import ti4.service.leader.agent.TargetedAgent;

public final class MentakAgent extends TargetedAgent {

    static final String ID = "mentakagent";

    public MentakAgent() {
        super(ID, "Suffi An, the Mentak");
    }

    public static Button button(Player user, Player pillaged) {
        return Buttons.green(
                ownedButtonId(user, ID, pillaged.getFaction()),
                useLabel(user, ID, "Mentak Agent"),
                FactionEmojis.Mentak);
    }

    @Override
    public AgentOutcome resolve(AgentUse<Player> use) {
        ActionCardHelper.drawActionCards(use.user(), 1);
        ActionCardHelper.drawActionCards(use.payload(), 1);
        return AgentOutcome.none();
    }
}
