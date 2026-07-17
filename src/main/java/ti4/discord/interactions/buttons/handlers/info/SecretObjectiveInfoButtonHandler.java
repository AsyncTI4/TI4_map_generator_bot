package ti4.discord.interactions.buttons.handlers.info;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.service.info.SecretObjectiveInfoService;

@UtilityClass
class SecretObjectiveInfoButtonHandler {

    @ButtonHandler(value = "refreshSOInfo", save = false)
    public static void sendSecretObjectiveInfo(Game game, Player player, ButtonInteractionEvent event) {
        SecretObjectiveInfoService.sendSecretObjectiveInfo(game, player, event);
    }
}
