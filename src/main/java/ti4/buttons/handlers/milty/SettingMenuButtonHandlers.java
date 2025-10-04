package ti4.buttons.handlers.milty;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;

@UtilityClass
class SettingMenuButtonHandlers {

    @ButtonHandler("jmfA_")
    @ButtonHandler("jmfN_")
    private void handleSettingMenuButton(ButtonInteractionEvent event, Game game) {
        // Detect new settings menu navId() to route to the correct handler.
        String draftSystemNavPart = ".*_draft[._].*";
        if (event.getCustomId().matches(draftSystemNavPart)) {
            game.initializeDraftSystemSettings().parseButtonInput(event);
            return;
        }

        game.initializeMiltySettings().parseButtonInput(event);
    }
}
