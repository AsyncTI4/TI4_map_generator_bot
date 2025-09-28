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
        // TODO: This straight up breaks if someone hits a classic Milty draft button, then later decides to start a Draft System draft
        // We need some way to determine which actually should receive the event.
        if(game.getDraftSystemSettingsUnsafe() != null || game.getDraftSystemSettingsJson() != null) {
            game.initializeDraftSystemSettings().parseButtonInput(event);
        } else if(game.getMiltySettingsUnsafe() != null || game.getMiltyJson() != null) {
            game.initializeMiltySettings().parseButtonInput(event);
        } else {
            event.reply("No Milty or Draft System settings found for this game. Please set up Milty or Draft System first.").setEphemeral(true).queue();
        }
    }
}
