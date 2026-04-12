package ti4.buttons.handlers.faction.kollect;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperFactionSpecific;
import ti4.listeners.annotations.ButtonHandler;
import ti4.message.MessageHelper;

@UtilityClass
class KollectButtonHandler {

    @ButtonHandler("getReleaseButtons")
    public static void getReleaseButtons(ButtonInteractionEvent event, Player player, Game game) {
        MessageHelper.sendMessageToChannelWithButtons(
                event.getChannel(),
                player.getRepresentationUnfogged()
                        + ", you may release units one at a time with the buttons. Reminder that captured units may only be released as part of an ability or a transaction.",
                ButtonHelperFactionSpecific.getReleaseButtons(player, game));
    }

    @ButtonHandler("shroudOfLithStart")
    public static void shroudOfLithStart(ButtonInteractionEvent event, Player player, Game game) {
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                "Select up to 2 ships and 2 ground forces to place in the space area.",
                ButtonHelperFactionSpecific.getKolleccReleaseButtons(player, game));
    }
}
