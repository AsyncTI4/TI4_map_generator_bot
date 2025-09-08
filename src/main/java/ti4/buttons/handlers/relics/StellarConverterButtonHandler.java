package ti4.buttons.handlers.relics;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.service.StellarConverterService;

@UtilityClass
class StellarConverterButtonHandler {

    @ButtonHandler("stellarConvert_")
    public static void resolveStellar(Game game, ButtonInteractionEvent event, String buttonID) {
        StellarConverterService.resolveStellar(game, event, buttonID);
    }
}
