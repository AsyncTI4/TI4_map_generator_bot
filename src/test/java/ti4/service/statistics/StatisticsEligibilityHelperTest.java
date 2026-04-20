package ti4.service.statistics;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.persistence.ManagedGame;
import ti4.spring.service.persistence.GameEntity;
import ti4.spring.service.persistence.PlayerEntity;
import ti4.testUtils.BaseTi4Test;

class StatisticsEligibilityHelperTest extends BaseTi4Test {

    @Test
    void rejectsGamesWithFewerThanThreePlayers() {
        Game twoPlayerGame = createGame("two-player", 2);
        Game threePlayerGame = createGame("three-player", 3);

        assertFalse(StatisticsEligibilityHelper.isEligibleForStatistics(twoPlayerGame));
        assertTrue(StatisticsEligibilityHelper.isEligibleForStatistics(threePlayerGame));
        assertFalse(StatisticsEligibilityHelper.isEligibleForStatistics(new ManagedGame(twoPlayerGame)));
        assertTrue(StatisticsEligibilityHelper.isEligibleForStatistics(new ManagedGame(threePlayerGame)));
    }

    @Test
    void filtersPlayerEntitiesByPersistedPlayerCount() {
        PlayerEntity twoPlayerEntity = createPlayerEntityWithPlayerCount(2);
        PlayerEntity threePlayerEntity = createPlayerEntityWithPlayerCount(3);

        List<PlayerEntity> filtered =
                StatisticsEligibilityHelper.filterPlayersInEligibleGames(List.of(twoPlayerEntity, threePlayerEntity));

        assertFalse(StatisticsEligibilityHelper.isEligibleForStatistics(twoPlayerEntity));
        assertTrue(StatisticsEligibilityHelper.isEligibleForStatistics(threePlayerEntity));
        assertTrue(filtered.contains(threePlayerEntity));
        assertFalse(filtered.contains(twoPlayerEntity));
    }

    private static Game createGame(String name, int playerCount) {
        Game game = new Game();
        game.setName(name);
        for (int i = 1; i <= playerCount; i++) {
            game.addPlayer(name + "-user-" + i, "Player " + i);
        }
        return game;
    }

    private static PlayerEntity createPlayerEntityWithPlayerCount(int playerCount) {
        GameEntity game = new GameEntity();
        game.setPlayerCount(playerCount);
        PlayerEntity player = new PlayerEntity();
        player.setGame(game);
        return player;
    }
}
