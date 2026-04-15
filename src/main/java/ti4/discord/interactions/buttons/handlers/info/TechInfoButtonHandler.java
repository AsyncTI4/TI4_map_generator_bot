package ti4.discord.interactions.buttons.handlers.info;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Player;
import ti4.helpers.Constants;
import ti4.service.info.TechInfoService;

@UtilityClass
class TechInfoButtonHandler {

    @ButtonHandler(value = Constants.REFRESH_TECH_INFO, save = false)
    public static void sendTechInfo(Player player, GenericInteractionCreateEvent event) {
        TechInfoService.sendTechInfo(player, event);
    }
}
