package ti4.discord.interactions.buttons.handlers.faction.discordantstars.toldar;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.message.MessageHelper;

@UtilityClass
class ToldarButtonHandler {

    @ButtonHandler("toldarPN")
    public static void toldarPN(ButtonInteractionEvent event, Player player) {
        player.setCommodities(player.getCommodities() + 3);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.toString() + " used _Concordat Allegiant_ (the Toldar promissory note)"
                        + " to gain 3 commodities after winning a combat against someone with more victory points than them. They can do this once per action. They currently hold "
                        + player.getCommodities() + " commodit" + (player.getCommodities() == 1 ? "y" : "ies") + ".");
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
    }
}
