package ti4.buttons.handlers.info;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.service.info.SecretObjectiveInfoService;

@UtilityClass
class SecretObjectiveInfoButtonHandler {

    @ButtonHandler("refreshSOInfo")
    public static void sendSecretObjectiveInfo(Game game, Player player, ButtonInteractionEvent event) {
        SecretObjectiveInfoService.sendSecretObjectiveInfo(game, player, event);
    }
}
