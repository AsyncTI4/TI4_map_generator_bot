package ti4.buttons.handlers.options;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.listeners.annotations.ButtonHandler;
import ti4.message.MessageHelper;
import ti4.service.player.PingIntervalService;
import ti4.settings.users.UserSettings;
import ti4.settings.users.UserSettingsManager;

@UtilityClass
class PingIntervalButtonHandler {

    @ButtonHandler(value = "playerPref_personalPingInterval", save = false)
    public static void offerPersonalPingOptions(GenericInteractionCreateEvent event) {
        List<Button> buttons = getPersonalAutoPingButtons();
        UserSettings userSettings = UserSettingsManager.get(event.getUser().getId());
        String message = event.getUser().getAsMention()
                + ", please choose the number of hours you would like the bot to wait before it pings you that it is your turn."
                + "\nThis will apply to __all__ of your games.\nYour current interval is `"
                + userSettings.getPersonalPingInterval() + "`.";
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, buttons);
    }

    private static List<Button> getPersonalAutoPingButtons() {
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.red("UserSetPersonalPingIntervalTo" + 0, "Turn Off"));
        for (int x = 1; x < 13; x++) {
            buttons.add(Buttons.gray("UserSetPersonalPingIntervalTo" + x, "" + x));
        }
        buttons.add(Buttons.gray("UserSetPersonalPingIntervalTo" + 24, "" + 24));
        buttons.add(Buttons.gray("UserSetPersonalPingIntervalTo" + 48, "" + 48));
        return buttons;
    }

    @ButtonHandler(value = "UserSetPersonalPingIntervalTo", save = false)
    public static void set(ButtonInteractionEvent event, String buttonID) {
        String pingIntervalRaw = buttonID.replace("UserSetPersonalPingIntervalTo", "");
        int pingInterval;
        try {
            pingInterval = Integer.parseInt(pingIntervalRaw);
        } catch (Exception e) {
            String message = "Error - could not set ping interval to: `" + pingIntervalRaw + "`";
            MessageHelper.sendMessageToEventChannel(event, message);
            return;
        }
        event.getMessage().delete().queue();
        PingIntervalService.set(event, pingInterval);
    }
}
