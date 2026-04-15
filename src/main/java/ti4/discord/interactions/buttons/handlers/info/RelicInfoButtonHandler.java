package ti4.discord.interactions.buttons.handlers.info;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.Constants;
import ti4.service.info.RelicInfoService;

@UtilityClass
class RelicInfoButtonHandler {

    @ButtonHandler(value = Constants.REFRESH_RELIC_INFO, save = false)
    public static void sendRelicInfo(Game game, Player player, GenericInteractionCreateEvent event) {
        RelicInfoService.sendRelicInfo(game, player, event);
    }
}
