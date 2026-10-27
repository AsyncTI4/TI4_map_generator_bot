package ti4.service.leader.agent.modules.homebrew;

import net.dv8tion.jda.api.components.buttons.Button;
import ti4.discord.interactions.buttons.Buttons;
import ti4.game.Player;
import ti4.helpers.ActionCardHelper;
import ti4.service.emoji.FactionEmojis;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentOutcome.Message;
import ti4.service.leader.agent.AgentUse;
import ti4.service.leader.agent.PlayerPickerAgent;

public final class VaylerianAgent extends PlayerPickerAgent {

    static final String ID = "vaylerianagent";

    public VaylerianAgent() {
        super(ID, "Yvin Korduul, the Vaylerian");
    }

    public static Button pickTargetButton(Player vaylerian) {
        return useButton(vaylerian, ID, "Vaylerian Agent", FactionEmojis.vaylerian);
    }

    public static Button button(Player vaylerian) {
        return Buttons.green(
                ownedButtonId(vaylerian, ID, vaylerian.getFaction()),
                useLabel(vaylerian, ID, "Vaylerian Agent"),
                FactionEmojis.vaylerian);
    }

    @Override
    protected AgentOutcome resolveFor(AgentUse<Player> use) {
        Player target = use.payload();
        ActionCardHelper.drawActionCards(target, 1);
        if (!use.game().isFowMode()) {
            return AgentOutcome.none();
        }
        return AgentOutcome.of(Message.plain(
                use.user(),
                target.getFactionEmojiOrColor() + " gained 1 action card from using " + use.agentName() + "."));
    }
}
