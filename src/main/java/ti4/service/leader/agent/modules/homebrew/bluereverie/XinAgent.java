package ti4.service.leader.agent.modules.homebrew.bluereverie;

import net.dv8tion.jda.api.components.buttons.Button;
import ti4.game.Player;
import ti4.helpers.ButtonHelperHeroes;
import ti4.service.emoji.FactionEmojis;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentOutcome.Message;
import ti4.service.leader.agent.AgentUse;
import ti4.service.leader.agent.PlayerPickerAgent;

public final class XinAgent extends PlayerPickerAgent {

    static final String ID = "xinagent";

    public XinAgent() {
        super(ID, "Liu, the Xin");
    }

    public static Button button(Player holder) {
        return useButton(holder, ID, "Xin Agent", FactionEmojis.xin);
    }

    @Override
    protected AgentOutcome resolveFor(AgentUse<Player> use) {
        Player target = use.payload();
        return AgentOutcome.of(Message.addressedTo(
                target,
                ", " + use.agentNameFor(target) + " allows you to follow an exhausted strategy card."
                        + " You must still spend 1 command token from your strategy pool to do so.",
                ButtonHelperHeroes.getSecondaryButtons(use.game())));
    }
}
