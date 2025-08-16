package ti4.service.player;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.message.MessageHelper;
import ti4.settings.users.UserSettings;
import ti4.settings.users.UserSettingsManager;

@UtilityClass
public class PingOnNextTurn {

    private static void set(GenericInteractionCreateEvent event, UserSettings settings, boolean ping) {
        settings.setPingOnNextTurn(ping);
        UserSettingsManager.save(settings);

        if (event == null) {
            return;
        }
        String message = "Set `Ping On Next Turn` to: `" + ping + "`.";
        MessageHelper.sendMessageToEventChannel(event, message);
    }

    public static void set(GenericInteractionCreateEvent event, boolean ping) {
        UserSettings userSettings = UserSettingsManager.get(event.getUser().getId());
        set(event, userSettings, ping);
    }
}
