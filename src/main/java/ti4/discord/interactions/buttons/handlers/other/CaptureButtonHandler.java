package ti4.discord.interactions.buttons.handlers.other;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelperFactionSpecific;
import ti4.message.MessageHelper;

@UtilityClass
class CaptureButtonHandler {

    @ButtonHandler("getReleaseButtons")
    public static void getReleaseButtons(ButtonInteractionEvent event, Player player, Game game) {
        MessageHelper.sendMessageToChannelWithButtons(
                event.getChannel(),
                player.getRepresentationUnfogged()
                        + ", you may release units one at a time with the buttons. Reminder that captured units may only be released as part of an ability or a transaction.",
                ButtonHelperFactionSpecific.getReleaseButtons(player, game));
    }
}
