package ti4.buttons.handlers.milty;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;

@UtilityClass
public class SettingMenuButtonHandlers {

    @ButtonHandler("jmfA_")
    @ButtonHandler("jmfN_")
    private void handleSettingMenuButton(ButtonInteractionEvent event, Game game) {
        game.initializeMiltySettings().parseButtonInput(event);
    }

}
