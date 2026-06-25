package ti4.discord.interactions.buttons.handlers.actioncards.acd2;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.message.MessageHelper;

@UtilityClass
class LiquidationAcd2ButtonHandler {

    @ButtonHandler("resolveLiquidation")
    public static void resolveLiquidation(Player player, ButtonInteractionEvent event) {
        List<Button> buttons = new ArrayList<>();
        for (int reduce = 1; reduce <= 3; reduce++) {
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + "liquidationReduce_" + reduce,
                    "Reduce PRODUCTION by " + reduce + " (cost -" + (reduce * 2) + ")"));
        }
        buttons.add(Buttons.red("deleteButtons", "Cancel"));
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", choose how much to reduce your total PRODUCTION value by for"
                        + " _Liquidation_. The combined cost of the produced units is reduced by twice that amount.",
                buttons);
    }

    @ButtonHandler("liquidationReduce_")
    public static void resolveLiquidationReduce(
            Player player, ButtonInteractionEvent event, String buttonID) {
        int reduce;
        try {
            reduce = Integer.parseInt(buttonID.replace("liquidationReduce_", ""));
        } catch (NumberFormatException e) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        ButtonHelper.deleteMessage(event);
        player.addSpentThing("liquidation" + reduce);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + " played _Liquidation_: their total PRODUCTION value is reduced by "
                        + reduce + " and the combined cost of the produced units is reduced by " + (reduce * 2)
                        + ". Re-open the build to see the adjusted PRODUCTION limit and cost.");
    }
}
