package ti4.discord.interactions.buttons.handlers.milty;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.helpers.settingsFramework.menus.FrankenSettings;

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
        if (FrankenSettings.isFrankenMenuComponent(event.getCustomId())) {
            game.initializeFrankenSettings().parseButtonInput(event);
            return;
        }

        game.initializeMiltySettings().parseButtonInput(event);
    }
}
