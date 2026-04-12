package ti4.buttons.handlers.info;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.game.Game;
import ti4.game.Player;
import ti4.listeners.annotations.ButtonHandler;
import ti4.service.info.AbilityInfoService;

@UtilityClass
class AbilityInfoButtonHandler {

    @ButtonHandler(value = "refreshAbilityInfo", save = false)
    public static void sendAbilityInfo(Game game, Player player, GenericInteractionCreateEvent event) {
        AbilityInfoService.sendAbilityInfo(player, event);
    }
}
