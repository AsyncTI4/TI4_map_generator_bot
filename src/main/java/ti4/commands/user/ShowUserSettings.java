package ti4.commands.user;

import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.message.MessageHelper;

public class ShowUserSettings extends UserSubcommandData {

    private static final ObjectMapper mapper = new ObjectMapper();

    public ShowUserSettings() {
        super("show_settings", "Show your User Settings");
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        showUserSettings(event, getUserSettings());
    }

    public static void showUserSettings(GenericInteractionCreateEvent event, UserSettings userSettings) {
        MessageHelper.sendMessageToChannelWithEmbedsAndButtons(
                event.getMessageChannel(),
                null,
                List.of(getSettingEmbed(event, userSettings)),
                getUserSettingsButtons());
    }

    private static MessageEmbed getSettingEmbed(GenericInteractionCreateEvent event, UserSettings userSettings) {
        EmbedBuilder eb = new EmbedBuilder();
        String userName = event.getUser().getName();
        eb.setTitle(userName + "'s User Settings");
        eb.addField("", "```json\n" + getJSONRaw(userSettings) + "```", false);
        return eb.build();
    }

    private static String getJSONRaw(UserSettings userSettings) {
        try {
            return mapper.writeValueAsString(userSettings);
        } catch (JsonProcessingException e) {
            return "Failed to serialize UserSettings";
        }
    }

    public static List<Button> getUserSettingsButtons() {
        return List.of(SetPersonalPingInterval.OFFER_PING_OPTIONS_BUTTON, Buttons.DONE_DELETE_BUTTONS);
    }
}
