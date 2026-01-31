package ti4.spring.api.image;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Stores per-player FoW (Fog of War) map Discord message references.
 * Each player in a FoW game has their own personalized map view sent to their DMs.
 * This entity stores the Discord message ID so the web frontend can retrieve the map image.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "player_map_image_data", uniqueConstraints = @UniqueConstraint(columnNames = {"game_name", "player_id"}))
class PlayerMapImageData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "game_name", nullable = false)
    private String gameName;

    @Column(name = "player_id", nullable = false)
    private String playerId;

    @Column(name = "discord_message_id")
    private Long discordMessageId;

    @Column(name = "discord_channel_id")
    private Long discordChannelId;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    PlayerMapImageData(String gameName, String playerId, Long discordMessageId, Long discordChannelId) {
        this.gameName = gameName;
        this.playerId = playerId;
        this.discordMessageId = discordMessageId;
        this.discordChannelId = discordChannelId;
        this.updatedAt = LocalDateTime.now();
    }
}
