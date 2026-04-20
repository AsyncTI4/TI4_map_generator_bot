package ti4.service.statistics;

import java.util.Collection;
import java.util.List;
import lombok.experimental.UtilityClass;
import ti4.game.Game;
import ti4.game.persistence.ManagedGame;
import ti4.spring.service.persistence.GameEntity;
import ti4.spring.service.persistence.PlayerEntity;

@UtilityClass
public class StatisticsEligibilityHelper {

    public static final int MINIMUM_PLAYER_COUNT = 3;

    public static boolean isEligibleForStatistics(Game game) {
        return game.getRealAndEliminatedPlayers().size() >= MINIMUM_PLAYER_COUNT;
    }

    public static boolean isEligibleForStatistics(ManagedGame game) {
        return game.getRealPlayers().size() >= MINIMUM_PLAYER_COUNT;
    }

    public static boolean isEligibleForStatistics(GameEntity game) {
        return game.getPlayerCount() >= MINIMUM_PLAYER_COUNT;
    }

    public static boolean isEligibleForStatistics(PlayerEntity player) {
        return isEligibleForStatistics(player.getGame());
    }

    public static <T extends PlayerEntity> List<T> filterPlayersInEligibleGames(Collection<T> players) {
        return players.stream().filter(StatisticsEligibilityHelper::isEligibleForStatistics).toList();
    }
}
