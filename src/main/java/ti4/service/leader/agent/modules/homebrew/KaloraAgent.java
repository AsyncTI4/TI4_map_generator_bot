package ti4.service.leader.agent.modules.homebrew;

import net.dv8tion.jda.api.components.buttons.Button;
import ti4.discord.interactions.buttons.Buttons;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.service.emoji.FactionEmojis;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentOutcome.Message;
import ti4.service.leader.agent.AgentUse;
import ti4.service.leader.agent.UntargetedAgent;

public final class KaloraAgent extends UntargetedAgent {

    static final String ID = "kaloraagent";

    public KaloraAgent() {
        super(ID, "Valzor, the Kalora");
    }

    public static Button button(Player kalora) {
        return Buttons.gray(
                ownedButtonId(kalora, ID), useLabel(kalora, ID, "Valzor, the Kalora Agent"), FactionEmojis.kalora);
    }

    @Override
    public AgentOutcome resolve(AgentUse<Player> use) {
        Player user = use.user();
        return AgentOutcome.of(Message.addressedTo(
                user, ", please use the buttons to gain 1 command token.", ButtonHelper.getGainCCButtons(user)));
    }
}
