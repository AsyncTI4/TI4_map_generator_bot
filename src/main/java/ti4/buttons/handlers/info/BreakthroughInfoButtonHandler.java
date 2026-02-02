package ti4.buttons.handlers.info;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.helpers.Constants;
import ti4.helpers.thundersedge.BreakthroughCommandHelper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;

@UtilityClass
class BreakthroughInfoButtonHandler {

    @ButtonHandler(value = Constants.REFRESH_BREAKTHROUGH_INFO, save = false)
    public static void sendBreakthroughInfo(Game game, Player player, GenericInteractionCreateEvent event) {
        BreakthroughCommandHelper.sendBreakthroughInfo(event, game, player, player);
    }
}
