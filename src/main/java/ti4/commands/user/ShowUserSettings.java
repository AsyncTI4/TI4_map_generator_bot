package ti4.commands.user;

import java.util.List;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.message.MessageHelper;

public class ShowUserSettings extends UserSubcommandData {

    public ShowUserSettings() {
        super("show_settings", "Show your User Settings");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        showUserSettings(event, getUserSettings());
    }

    public static void showUserSettings(GenericInteractionCreateEvent event, UserSettings userSettings) {
        MessageHelper.sendMessageToChannelWithEmbedsAndButtons(
            event.getMessageChannel(),
            null,
            List.of(userSettings.getSettingEmbed()),
            userSettings.getUserSettingsButtons());
    }
}
