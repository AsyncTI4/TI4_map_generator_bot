package ti4.spring.api.image;

import static software.amazon.awssdk.utils.StringUtils.isBlank;

import java.time.LocalDateTime;
import java.util.Optional;
import javax.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;
import ti4.game.persistence.GameManager;
import ti4.service.persistence.SqlitePersistenceGate;

@Service
@RequiredArgsConstructor
public class GameImageService {

    private final MapImageDataRepository mapImageDataRepository;
    private final PlayerMapImageDataRepository playerMapImageDataRepository;

    @NotNull
    Optional<MapImageData> getLatestMapImageData(String gameName) {
        if (SqlitePersistenceGate.isDisabled()) return Optional.empty();
        if (!GameManager.isValid(gameName)) return Optional.empty();
        return mapImageDataRepository.findById(gameName);
    }

    @Nullable
    public String getLatestMapImageName(String gameName) {
        if (SqlitePersistenceGate.isDisabled()) return null;
        if (!GameManager.isValid(gameName)) return null;
        return mapImageDataRepository
                .findById(gameName)
                .map(MapImageData::getLatestMapImageName)
                .orElse(null);
    }

    public void saveDiscordMessageId(String gameName, long discordMessageId, long guildId, long channelId) {
        if (SqlitePersistenceGate.isDisabled()) return;
        if (isBlank(gameName) || !GameManager.isValid(gameName)) return;
        MapImageData mapImageData = loadOrCreate(gameName);
        mapImageData.setLatestDiscordMessageId(discordMessageId);
        mapImageData.setLatestDiscordGuildId(guildId);
        mapImageData.setLatestDiscordChannelId(channelId);
        mapImageDataRepository.save(mapImageData);
    }

    public void saveDiscordMessage(String gameName, Message message) {
        if (isBlank(gameName) || message == null) return;
        Guild guild = message.getGuild();
        MessageChannel channel = message.getChannel();
        saveDiscordMessageId(gameName, message.getIdLong(), guild.getIdLong(), channel.getIdLong());
    }

    private MapImageData loadOrCreate(String gameName) {
        return mapImageDataRepository
                .findById(gameName)
                .orElseGet(() -> new MapImageData(gameName, null, null, null, null));
    }

    /**
     * Save the Discord message ID for a player's FoW map.
     * This allows the web frontend to retrieve the player's personalized map.
     */
    public void savePlayerDiscordMessageId(String gameName, String playerId, long messageId, long channelId) {
        if (SqlitePersistenceGate.isDisabled()) return;
        if (isBlank(gameName) || isBlank(playerId)) return;

        PlayerMapImageData data = playerMapImageDataRepository
                .findByGameNameAndPlayerId(gameName, playerId)
                .orElseGet(() -> new PlayerMapImageData(gameName, playerId, null, null));

        data.setDiscordMessageId(messageId);
        data.setDiscordChannelId(channelId);
        data.setUpdatedAt(LocalDateTime.now());
        playerMapImageDataRepository.save(data);
    }

    /**
     * Get the stored FoW map data for a specific player in a game.
     */
    @NotNull
    Optional<PlayerMapImageData> getPlayerMapImageData(String gameName, String playerId) {
        if (SqlitePersistenceGate.isDisabled()) return Optional.empty();
        if (isBlank(gameName) || isBlank(playerId)) return Optional.empty();
        return playerMapImageDataRepository.findByGameNameAndPlayerId(gameName, playerId);
    }
}
