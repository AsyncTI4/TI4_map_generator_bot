package ti4.service.leader.agent.modules.homebrew;

import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ActionCardHelper;
import ti4.service.emoji.FactionEmojis;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentOutcome.Message;
import ti4.service.leader.agent.AgentUse;
import ti4.service.leader.agent.PlayerPickerAgent;

public final class VaylerianAgent extends PlayerPickerAgent {

    static final String ID = "vaylerianagent";
    private static final String PICK_TARGET_PREFIX = "vaylerianAgent";

    public VaylerianAgent() {
        super(ID, "Yvin Korduul, the Vaylerian", PICK_TARGET_PREFIX);
    }

    public static Button pickTargetButton(Player vaylerian) {
        return Buttons.gray(
                ownedButtonId(vaylerian, ID), useLabel(vaylerian, ID, "Vaylerian Agent"), FactionEmojis.vaylerian);
    }

    public static Button button(Player vaylerian) {
        return Buttons.green(
                ownedButtonId(vaylerian, ID, vaylerian.getFaction()),
                useLabel(vaylerian, ID, "Vaylerian Agent"),
                FactionEmojis.vaylerian);
    }

    @ButtonHandler(PICK_TARGET_PREFIX + "_")
    public static void resolvePickedTarget(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        resolvePickedPlayer(ID, buttonID, event, game, player);
    }

    @Override
    protected AgentOutcome resolveFor(AgentUse<Choice> use, Player target) {
        ActionCardHelper.drawActionCards(target, 1);
        if (!use.game().isFowMode()) {
            return AgentOutcome.none();
        }
        return AgentOutcome.of(Message.plain(
                use.user(),
                target.getFactionEmojiOrColor() + " gained 1 action card from using " + use.agentName() + "."));
    }
}
