package ti4.discord.interactions.buttons.handlers.info;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.Constants;
import ti4.helpers.thundersedge.BreakthroughCommandHelper;

@UtilityClass
class BreakthroughInfoButtonHandler {

    @ButtonHandler(value = Constants.REFRESH_BREAKTHROUGH_INFO, save = false)
    public static void sendBreakthroughInfo(Game game, Player player, GenericInteractionCreateEvent event) {
        BreakthroughCommandHelper.sendBreakthroughInfo(event, game, player, player);
    }
}
