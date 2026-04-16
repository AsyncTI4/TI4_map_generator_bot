package ti4.discord.interactions.buttons.handlers.faction.discordantstars.kollect;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperFactionSpecific;
import ti4.message.MessageHelper;

@UtilityClass
class KollectButtonHandler {

    @ButtonHandler("shroudOfLithStart")
    public static void shroudOfLithStart(ButtonInteractionEvent event, Player player, Game game) {
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                "Select up to 2 ships and 2 ground forces to place in the space area.",
                ButtonHelperFactionSpecific.getKolleccReleaseButtons(player, game));
    }
}
