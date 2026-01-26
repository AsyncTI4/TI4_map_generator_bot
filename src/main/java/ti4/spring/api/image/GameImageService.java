package ti4.spring.api.image;

import static software.amazon.awssdk.utils.StringUtils.isBlank;

import javax.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ti4.map.Game;
import ti4.map.persistence.GameManager;

@Service
@RequiredArgsConstructor
public class GameImageService {

    private final MapImageDataRepository mapImageDataRepository;

    @Nullable
    MapImageData getLatestMapImageData(String gameName) {
        if (!GameManager.isValid(gameName)) return null;
        return mapImageDataRepository
                .findById(gameName)
                .orElse(null);
    }

    public void saveMapImageName(Game game, String mapImageName) {
        if (game == null || isBlank(mapImageName)) return;
        MapImageData mapImageData = loadOrCreate(game.getName());
        mapImageData.setLatestMapImageName(mapImageName);
        mapImageDataRepository.save(mapImageData);
    }

    public void saveDiscordMessageId(Game game, long discordMessageId, long guildId, long channelId) {
        if (game == null || !GameManager.isValid(game.getName())) return;
        MapImageData mapImageData = loadOrCreate(game.getName());
        mapImageData.setLatestDiscordMessageId(discordMessageId);
        mapImageData.setLatestDiscordGuildId(guildId);
        mapImageData.setLatestDiscordChannelId(channelId);
        mapImageDataRepository.save(mapImageData);
    }

    private MapImageData loadOrCreate(String gameName) {
        return mapImageDataRepository
                .findById(gameName)
                .orElseGet(() -> new MapImageData(gameName, null, null, null, null));
    }
}
