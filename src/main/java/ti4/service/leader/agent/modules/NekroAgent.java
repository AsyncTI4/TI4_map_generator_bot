package ti4.service.leader.agent.modules;

import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ActionCardHelper;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.ButtonHelperAgents;
import ti4.service.emoji.FactionEmojis;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentOutcome.Message;
import ti4.service.leader.agent.AgentUse;
import ti4.service.leader.agent.PlayerPickerAgent;

public final class NekroAgent extends PlayerPickerAgent {

    static final String ID = "nekroagent";
    private static final String PICK_PLAYER_PREFIX = "nekroAgentRes";

    public NekroAgent() {
        super(ID, "Nekro Malleon, the Nekro", PICK_PLAYER_PREFIX);
    }

    public static Button button(Player nekro) {
        return Buttons.gray(ownedButtonId(nekro, ID), useLabel(nekro, ID, "Nekro Agent"), FactionEmojis.Nekro);
    }

    @ButtonHandler(PICK_PLAYER_PREFIX + "_")
    public static void resolvePickedPlayer(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        resolvePickedPlayer(ID, buttonID, event, game, player);
    }

    @Override
    protected AgentOutcome resolveFor(AgentUse<Choice> use, Player target) {
        Game game = use.game();
        String tradeGoods = target.gainTG(2);
        ButtonHelperAbilities.pillageCheck(target, game);
        ButtonHelperAgents.resolveArtunoCheck(target, 2);
        game.setStoredValue("originalCCsFor" + target.getFaction(), target.getCCRepresentation());
        return AgentOutcome.of(
                        Message.plain(
                                use.user(),
                                use.user().getFactionEmojiOrColor() + " selected " + target.getFactionEmojiOrColor()
                                        + " as user of " + use.agentName() + "."),
                        Message.addressedTo(
                                target,
                                ", your trade goods increased by 2 " + tradeGoods
                                        + ". Use buttons in your `#cards-info` thread to discard 1 action card,"
                                        + " or below to remove 1 command token."),
                        Message.addressedTo(
                                        target,
                                        ", please choose the action card to discard.",
                                        ActionCardHelper.getDiscardActionCardButtons(target, false))
                                .inCardsInfoThread())
                .and(Message.addressedTo(
                        target,
                        ", your current command tokens are " + target.getCCRepresentation()
                                + ". Use buttons to lose tokens.",
                        ButtonHelper.getLoseCCButtons(target)));
    }
}
