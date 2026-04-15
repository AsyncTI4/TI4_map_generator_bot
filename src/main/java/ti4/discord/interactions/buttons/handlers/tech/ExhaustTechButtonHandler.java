package ti4.discord.interactions.buttons.handlers.tech;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.service.tech.PlayerTechService;

@UtilityClass
class ExhaustTechButtonHandler {

    @ButtonHandler("exhaustTech_")
    public static void exhaustTech(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String tech = buttonID.replace("exhaustTech_", "");
        PlayerTechService.exhaustTechAndResolve(event, game, player, tech);
    }
}
