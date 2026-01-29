package ti4.commands.user;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.util.List;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.buttons.Buttons;
import ti4.commands.Subcommand;
import ti4.message.MessageHelper;
import ti4.settings.users.UserSettings;
import ti4.settings.users.UserSettingsManager;

class ShowUserSettings extends Subcommand {

    private static final ObjectMapper mapper = new ObjectMapper();

    ShowUserSettings() {
        super("show_settings", "Show your User Settings");
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                getSettingEmbed(event, UserSettingsManager.get(event.getUser().getId())),
                getUserSettingsButtons());
    }

    private static String getSettingEmbed(GenericInteractionCreateEvent event, UserSettings userSettings) {
        StringBuilder eb = new StringBuilder();
        String userName = event.getUser().getName();
        eb.append(userName + "'s User Settings\n");
        eb.append("```json\n" + getJSONRaw(userSettings) + "```");
        return eb.toString();
    }

    private static String getJSONRaw(UserSettings userSettings) {
        try {
            return mapper.writeValueAsString(userSettings);
        } catch (JsonProcessingException e) {
            return "Failed to serialize UserSettings";
        }
    }

    private static List<Button> getUserSettingsButtons() {
        return List.of(Buttons.OFFER_PING_OPTIONS_BUTTON, Buttons.DONE_DELETE_BUTTONS);
    }
}
