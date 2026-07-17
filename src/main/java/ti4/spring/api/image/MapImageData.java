package ti4.spring.api.image;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "map_image_data")
class MapImageData {
    @Id
    private String gameName;

    private String latestMapImageName;

    @Column(name = "latest_discord_message_id")
    private Long latestDiscordMessageId;

    @Column(name = "latest_discord_guild_id")
    private Long latestDiscordGuildId;

    @Column(name = "latest_discord_channel_id")
    private Long latestDiscordChannelId;
}
