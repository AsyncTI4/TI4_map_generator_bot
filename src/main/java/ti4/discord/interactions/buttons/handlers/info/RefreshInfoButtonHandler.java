package ti4.discord.interactions.buttons.handlers.info;

import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.message.MessageHelper;

@UtilityClass
class RefreshInfoButtonHandler {

    @ButtonHandler(value = "refreshInfoButtons", save = false)
    public static void sendRefreshInfoButtons(ButtonInteractionEvent event, Game game) {
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), null, getRefreshInfoButtons(game));
    }

    private static List<Button> getRefreshInfoButtons(Game game) {
        if (game == null) return Buttons.REFRESH_INFO_BUTTONS;
        if (game.isTwilightsFallMode()) return Buttons.REFRESH_INFO_BUTTONS_TF;
        if (game.isThundersEdge()) return Buttons.REFRESH_INFO_BUTTONS_TE;
        return Buttons.REFRESH_INFO_BUTTONS;
    }
}
