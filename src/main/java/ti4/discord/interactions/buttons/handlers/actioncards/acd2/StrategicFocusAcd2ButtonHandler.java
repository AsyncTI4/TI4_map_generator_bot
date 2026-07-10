package ti4.discord.interactions.buttons.handlers.actioncards.acd2;

import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.helpers.thundersedge.TeHelperActionCards;
import ti4.message.MessageHelper;

@UtilityClass
class StrategicFocusAcd2ButtonHandler {

    @ButtonHandler("resolveStrategicFocus")
    public static void resolveStrategicFocus(Player player, Game game, ButtonInteractionEvent event) {
        List<Button> buttons = TeHelperActionCards.getReadiedStrategyCardSecondaryButtons(game, player);
        buttons.add(Buttons.red("deleteButtons", "Done Resolving"));
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged()
                        + ", choose the secondary ability of a readied or unchosen strategy card to resolve for"
                        + " _Strategic Focus_. No command token is spent.",
                buttons);
    }
}
