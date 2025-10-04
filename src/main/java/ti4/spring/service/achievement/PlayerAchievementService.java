package ti4.spring.service.achievement;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ti4.map.Game;
import ti4.map.Player;

@Service
@RequiredArgsConstructor
public class PlayerAchievementService {

    public static final String DRAGON_HOARD_KEY = "DRAGON_HOARD";
    public static final String DRAGON_HOARD_NAME = "Dragon Hoard";

    private final PlayerAchievementRepository playerAchievementRepository;

    @Transactional
    public void recordDragonHoard(Player player, Game game) {
        if (player == null || game == null) {
            return;
        }
        String gameMode = resolveGameMode(game);
        recordAchievement(player, DRAGON_HOARD_KEY, DRAGON_HOARD_NAME, gameMode);
    }

    private void recordAchievement(Player player, String achievementKey, String achievementName, String gameMode) {
        PlayerAchievement achievement = playerAchievementRepository
                .findByUserIdAndAchievementKeyAndGameMode(player.getUserID(), achievementKey, gameMode)
                .orElseGet(() -> new PlayerAchievement(
                        player.getUserID(), player.getUserName(), achievementKey, achievementName, gameMode));
        achievement.incrementCount(player.getUserName());
        playerAchievementRepository.save(achievement);
    }

    private String resolveGameMode(Game game) {
        if (game.isThundersEdge()) {
            return "TE";
        }
        if (game.isDiscordantStarsMode()) {
            return "DS";
        }
        if (game.isAbsolMode()) {
            return "Absol";
        }
        if (game.hasHomebrew()) {
            return "other homebrew";
        }
        return game.isBaseGameMode() ? "base" : "PoK";
    }
}
