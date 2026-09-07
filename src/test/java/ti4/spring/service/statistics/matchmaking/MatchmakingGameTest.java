package ti4.spring.service.statistics.matchmaking;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import ti4.spring.service.persistence.GameEntity;
import ti4.spring.service.persistence.PlayerEntity;
import ti4.spring.service.persistence.UserEntity;

class MatchmakingGameTest {

    private static final long ENDED_EPOCH_MILLIS = 1_700_000_000_000L;

    @Test
    void usesTheStoredSimulatedRanks() {
        List<PlayerEntity> players = gameWithRanks("game1", 1, 2, 3, 4, 5, 6);

        List<MatchmakingGame> games = MatchmakingGame.getMatchmakingGames(players);

        assertThat(games).hasSize(1);
        assertThat(games.getFirst().players())
                .extracting(MatchmakingPlayer::rank)
                .containsExactly(1, 2, 3, 4, 5, 6);
    }

    @Test
    void keepsTiedRanksAsTies() {
        List<PlayerEntity> players = gameWithRanks("game1", 1, 2, 2, 4, 5, 5);

        List<MatchmakingGame> games = MatchmakingGame.getMatchmakingGames(players);

        assertThat(games.getFirst().players())
                .extracting(MatchmakingPlayer::rank)
                .containsExactly(1, 2, 2, 4, 5, 5);
    }

    @Test
    void dropsAnyGameWhereAPlayerHasNoSimulatedRank() {
        List<PlayerEntity> players = gameWithRanks("game1", 1, 2, 3, null, 5, 6);

        assertThat(MatchmakingGame.getMatchmakingGames(players)).isEmpty();
    }

    @Test
    void oneUnrankedGameDoesNotDiscardTheOthers() {
        List<PlayerEntity> players = new ArrayList<>();
        players.addAll(gameWithRanks("ranked", 1, 2, 3, 4, 5, 6));
        players.addAll(gameWithRanks("unranked", 1, 2, 3, 4, 5, null));
        players.addAll(gameWithRanks("alsoRanked", 1, 2, 3, 4, 5, 6));

        List<MatchmakingGame> games = MatchmakingGame.getMatchmakingGames(players);

        assertThat(games).extracting(MatchmakingGame::name).containsExactly("ranked", "alsoRanked");
    }

    @Test
    void returnsAMutableListBecauseTheRatingServiceSortsItInPlace() {
        List<MatchmakingGame> games = MatchmakingGame.getMatchmakingGames(gameWithRanks("game1", 1, 2, 3));

        assertThat(games).isInstanceOf(ArrayList.class);
    }

    private static List<PlayerEntity> gameWithRanks(String gameName, Integer... simulatedRanks) {
        var gameEntity = new GameEntity();
        gameEntity.setGameName(gameName);
        gameEntity.setEndedEpochMilliseconds(ENDED_EPOCH_MILLIS);
        gameEntity.setVictoryPointGoal(10);

        List<PlayerEntity> players = new ArrayList<>();
        for (int i = 0; i < simulatedRanks.length; i++) {
            var playerEntity = new PlayerEntity();
            playerEntity.setGame(gameEntity);
            playerEntity.setUser(new UserEntity(gameName + "-user" + i, "player" + i));
            playerEntity.setSimulatedRank(simulatedRanks[i]);
            players.add(playerEntity);
        }
        return players;
    }
}
