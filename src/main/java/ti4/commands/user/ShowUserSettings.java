package ti4.commands.user;

import java.util.List;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands.Subcommand;
import ti4.message.MessageHelper;

public class ShowUserSettings extends Subcommand {

    public ShowUserSettings() {
        super("show_settings", "Show your User Settings");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        var userSettings = UserSettingsManager.getInstance().getUserSettings(event.getUser().getId());
        showUserSettings(event, userSettings);
    }

    public static void showUserSettings(GenericInteractionCreateEvent event, UserSettings userSettings) {
        MessageHelper.sendMessageToChannelWithEmbedsAndButtons(
            event.getMessageChannel(),
            null,
            List.of(userSettings.getSettingEmbed()),
            userSettings.getUserSettingsButtons());
    }
}
