package ti4.buttons.handlers;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.service.AbilityInfoService;

@UtilityClass
class AbilityInfoButtonHandler {

    @ButtonHandler("refreshAbilityInfo")
    public static void sendAbilityInfo(Game game, Player player, GenericInteractionCreateEvent event) {
        AbilityInfoService.sendAbilityInfo(game, player, event);
    }
}
