package ti4.discord.interactions.buttons.handlers.actioncards.acd2;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.function.Consumers;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelperAgents;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;

@UtilityClass
class AncientTradeRoutesAcd2ButtonHandler {

    @ButtonHandler("resolveAncientTradeRoutes")
    public static void resolveAncientTradeRoutes(Player player, Game game, ButtonInteractionEvent event) {
        List<Button> buttons = new ArrayList<>();
        player.setCommodities(player.getCommodities() + 2);
        ButtonHelperAgents.toldarAgentInitiation(game, player, 2);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(), player.getFactionEmoji() + " gained 2 commodities.");
        for (Player p2 : game.getRealPlayers()) {
            if (p2 == player) {
                continue;
            }
            if (game.isFowMode()) {
                buttons.add(Buttons.gray("ancientTradeRoutesStep2_" + p2.getFaction(), p2.getColor()));
            } else {
                Button button = Buttons.gray(
                        "ancientTradeRoutesStep2_" + p2.getFaction(),
                        p2.getFactionModel().getShortName());
                String factionEmojiString = p2.getFactionEmoji();
                button = button.withEmoji(Emoji.fromFormatted(factionEmojiString));
                buttons.add(button);
            }
        }
        buttons.add(Buttons.red("deleteButtons", "Don't Give Commodities"));
        event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", please choose the player you wish to give 2 commodities to.",
                buttons);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged()
                        + ", please choose a __different__ player you wish to give 2 commodities to.",
                buttons);
    }

    @ButtonHandler("ancientTradeRoutesStep2_")
    public static void resolveAncientTradeRoutesStep2(
            Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String faction = buttonID.split("_")[1];
        Player p2 = game.getPlayerFromColorOrFaction(faction);
        if (p2 == null) return;
        p2.setCommodities(p2.getCommodities() + 2);
        ButtonHelperAgents.toldarAgentInitiation(game, p2, 2);
        MessageHelper.sendMessageToChannel(
                p2.getCorrectChannel(),
                p2.getFactionEmoji() + " gained 2 commodities due to _Ancient Trade Routes_ and may transact with "
                        + player.getFactionEmojiOrColor() + " for this turn.");
        event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
    }
}
