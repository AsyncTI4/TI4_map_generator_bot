package ti4.commands.user;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.Data;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.AsyncTI4DiscordBot;
import ti4.buttons.Buttons;

@Data
public class UserSettings {

    private String userId;
    private List<String> preferredColourList = new ArrayList<>();
    private Integer personalPingInterval = 0;
    private Map<String, String> storedValues = new HashMap<>();

    public UserSettings(String userId) {
        this.userId = userId;
    }

    public UserSettings() {
    }

    @JsonIgnore
    public String getJSONRaw() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        try {
            return mapper.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            return "Failed to serialize UserSettings";
        }
    }

    @JsonIgnore
    public void save() {
        UserSettingsManager.getInstance().saveUserSetting(this);
    }

    @JsonIgnore
    public MessageEmbed getSettingEmbed() {
        EmbedBuilder eb = new EmbedBuilder();
        String userName = AsyncTI4DiscordBot.jda.getUserById(getUserId()).getName();
        eb.setTitle(userName + "'s User Settings");
        eb.addField("", "```json\n" + getJSONRaw() + "```", false);
        return eb.build();
    }

    @JsonIgnore
    public List<Button> getUserSettingsButtons() {
        return List.of(
            SetPersonalPingInterval.OFFER_PING_OPTIONS_BUTTON,
            Buttons.DONE_DELETE_BUTTONS);
    }

    public void putStoredValue(String settingKey, String settingValue) {
        storedValues.put(settingKey, settingValue);
    }

    public Optional<String> getStoredValue(String settingKey) {
        return Optional.ofNullable(storedValues.get(settingKey));
    }

    public String removeStoredValue(String settingKey) {
        return storedValues.remove(settingKey);
    }
}
