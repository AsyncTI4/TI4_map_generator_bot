package ti4.discord.interactions.buttons.handlers.relics;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.service.relic.StellarConverterService;

@UtilityClass
class StellarConverterButtonHandler {

    @ButtonHandler("stellarConvert_")
    public static void resolveStellar(Game game, ButtonInteractionEvent event, String buttonID, Player player) {
        StellarConverterService.resolveStellar(game, event, buttonID, player);
    }
}
