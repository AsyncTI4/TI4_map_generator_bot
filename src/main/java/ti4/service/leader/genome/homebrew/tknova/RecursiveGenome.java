package ti4.service.leader.genome.homebrew.tknova;

import java.util.List;
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
import ti4.message.MessageHelper;
import ti4.service.emoji.FactionEmojis;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentOutcome.Message;
import ti4.service.leader.agent.AgentPrompts;
import ti4.service.leader.agent.AgentUse;
import ti4.service.leader.agent.PlayerPickerAgent;
import ti4.service.leader.genome.LeaningGenome;

public final class RecursiveGenome extends LeaningGenome<PlayerPickerAgent.Choice> {

    static final String ID = "tknovarecursiveagent";
    private static final String STEP = "tknovaRecursive_";
    private static final String SPEND_TRADE_GOODS = STEP + "spendTg";
    private static final String DRAW_ACTION_CARD = STEP + "drawAc";
    private static final String GAIN_TRADE_GOODS = STEP + "gainTg";
    private static final int TRADE_GOODS = 2;

    public RecursiveGenome() {
        super(ID, "Recursive Genome", new Choices());
    }

    public static Button button(Player holder) {
        return Buttons.gray(ownedButtonId(holder, ID), useLabel(holder, ID, "Recursive Genome"), FactionEmojis.Nekro);
    }

    @ButtonHandler(STEP)
    static void resolveStep(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        switch (buttonID) {
            case SPEND_TRADE_GOODS -> spendTradeGoods(event, player);
            case DRAW_ACTION_CARD -> drawActionCard(event, player);
            case GAIN_TRADE_GOODS -> gainTradeGoods(event, game, player);
            default ->
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Unknown Recursive Genome choice.");
        }
    }

    private static void spendTradeGoods(ButtonInteractionEvent event, Player player) {
        if (player.getTg() < TRADE_GOODS) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "You do not have " + TRADE_GOODS + " trade goods.");
            return;
        }
        player.setTg(player.getTg() - TRADE_GOODS);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + " spent " + TRADE_GOODS + " trade goods.");
        ButtonHelper.deleteMessage(event);
    }

    private static void drawActionCard(ButtonInteractionEvent event, Player player) {
        ActionCardHelper.drawActionCardsSilent(player, 1);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + " drew 1 action card. Check your `#cards-info` thread.");
        ButtonHelper.deleteMessage(event);
    }

    private static void gainTradeGoods(ButtonInteractionEvent event, Game game, Player player) {
        String gained = player.gainTG(TRADE_GOODS);
        ButtonHelperAbilities.pillageCheck(player, game);
        ButtonHelperAgents.resolveArtunoCheck(player, TRADE_GOODS);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + " gained " + TRADE_GOODS + " trade goods " + gained + ".");
        ButtonHelper.deleteMessage(event);
    }

    private static final class Choices extends PlayerPickerAgent {

        Choices() {
            super(ID, "Nekro Malleon, the Nekro");
        }

        @Override
        protected AgentOutcome resolveFor(AgentUse<Player> use) {
            Game game = use.game();
            Player target = use.payload();
            String checker = target.factionButtonChecker();
            return AgentOutcome.of(
                    pickNote(use),
                    Message.addressedTo(
                            target,
                            ", you may pay one cost to resolve " + use.agentNameFor(target)
                                    + ": discard 1 action card, spend 1 command token, or spend " + TRADE_GOODS
                                    + " trade goods. Then take one reward, using the buttons in the following messages."),
                    Message.addressedTo(
                                    target,
                                    ", please choose the action card to discard.",
                                    ActionCardHelper.getDiscardActionCardButtons(target, false))
                            .inCardsInfoThread(),
                    AgentPrompts.loseCommandTokens(game, target),
                    Message.addressedTo(
                            target,
                            ", or spend " + TRADE_GOODS + " trade goods.",
                            List.of(Buttons.red(checker + SPEND_TRADE_GOODS, "Spend " + TRADE_GOODS + " Trade Goods"))),
                    Message.addressedTo(
                            target,
                            ", after paying, choose a reward: draw 1 action card, gain 1 command token, or gain "
                                    + TRADE_GOODS + " trade goods.",
                            List.of(
                                    Buttons.green(checker + DRAW_ACTION_CARD, "Draw 1 Action Card"),
                                    Buttons.green(checker + GAIN_TRADE_GOODS, "Gain " + TRADE_GOODS + " Trade Goods"))),
                    AgentPrompts.gainCommandTokens(game, target));
        }
    }
}
