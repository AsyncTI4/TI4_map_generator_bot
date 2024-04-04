package ti4.settings;

import java.util.List;
import java.util.Map;

import lombok.Data;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import ti4.AsyncTI4DiscordBot;

@Data
public class UserSettings {
    private String userId;
    private List<String> preferredColourList;
    private String funEmoji;
    private Map<String, String> otherSettings;

    public MessageEmbed getSettingEmbed() {
        EmbedBuilder eb = new EmbedBuilder();
        String userName = AsyncTI4DiscordBot.jda.getUserById(getUserId()).getName();
        eb.setTitle(userName + "'s User Settings");

        return eb.build();
    }

    public void addOtherSetting(String settingKey, String settingValue) {
        otherSettings.put(settingKey, settingValue);
    }

}
