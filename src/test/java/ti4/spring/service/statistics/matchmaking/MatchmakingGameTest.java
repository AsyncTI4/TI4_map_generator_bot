package ti4.spring.service.statistics.matchmaking;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import ti4.spring.service.persistence.GameEntity;
import ti4.spring.service.persistence.PlayerEntity;
import ti4.spring.service.persistence.UserEntity;

class MatchmakingGameTest {

    private static final long ENDED_EPOCH_MILLIS = 1_700_000_000_000L;

    @Test
    void usesTheSuppliedSimulatedRanks() {
        List<PlayerEntity> players = gamePlayers("game1", 6);
        Map<String, Map<String, Integer>> ranks = ranksFor("game1", 1, 2, 3, 4, 5, 6);

        List<MatchmakingGame> games = MatchmakingGame.getMatchmakingGames(players, ranks::get);

        assertThat(games).hasSize(1);
        assertThat(games.getFirst().players())
                .extracting(MatchmakingPlayer::rank)
                .containsExactly(1, 2, 3, 4, 5, 6);
    }

    @Test
    void keepsTiedRanksAsTies() {
        List<PlayerEntity> players = gamePlayers("game1", 6);
        Map<String, Map<String, Integer>> ranks = ranksFor("game1", 1, 2, 2, 4, 5, 5);

        List<MatchmakingGame> games = MatchmakingGame.getMatchmakingGames(players, ranks::get);

        assertThat(games.getFirst().players())
                .extracting(MatchmakingPlayer::rank)
                .containsExactly(1, 2, 2, 4, 5, 5);
    }

    @Test
    void dropsAGameWithNoSimulatedRanks() {
        List<PlayerEntity> players = gamePlayers("game1", 6);

        assertThat(MatchmakingGame.getMatchmakingGames(players, gameName -> Map.of()))
                .isEmpty();
    }

    @Test
    void dropsAGameWhereAPlayerIsMissingARank() {
        List<PlayerEntity> players = gamePlayers("game1", 6);
        Map<String, Map<String, Integer>> ranks = ranksFor("game1", 1, 2, 3, 4, 5, 6);
        ranks.get("game1").remove("game1-user3");

        assertThat(MatchmakingGame.getMatchmakingGames(players, ranks::get)).isEmpty();
    }

    @Test
    void oneUnrankedGameDoesNotDiscardTheOthers() {
        List<PlayerEntity> players = new ArrayList<>();
        players.addAll(gamePlayers("ranked", 6));
        players.addAll(gamePlayers("unranked", 6));
        players.addAll(gamePlayers("alsoRanked", 6));

        Map<String, Map<String, Integer>> ranks = ranksFor("ranked", 1, 2, 3, 4, 5, 6);
        ranks.putAll(ranksFor("alsoRanked", 1, 2, 3, 4, 5, 6));

        List<MatchmakingGame> games = MatchmakingGame.getMatchmakingGames(players, ranks::get);

        assertThat(games).extracting(MatchmakingGame::name).containsExactly("ranked", "alsoRanked");
    }

    @Test
    void returnsAMutableListBecauseTheRatingServiceSortsItInPlace() {
        Map<String, Map<String, Integer>> ranks = ranksFor("game1", 1, 2, 3);

        List<MatchmakingGame> games = MatchmakingGame.getMatchmakingGames(gamePlayers("game1", 3), ranks::get);

        assertThat(games).isInstanceOf(ArrayList.class);
    }

    private static Map<String, Map<String, Integer>> ranksFor(String gameName, Integer... ranks) {
        Map<String, Integer> byUser = new HashMap<>();
        for (int i = 0; i < ranks.length; i++) {
            byUser.put(gameName + "-user" + i, ranks[i]);
        }
        Map<String, Map<String, Integer>> byGame = new HashMap<>();
        byGame.put(gameName, byUser);
        return byGame;
    }

    private static List<PlayerEntity> gamePlayers(String gameName, int playerCount) {
        var gameEntity = new GameEntity();
        gameEntity.setGameName(gameName);
        gameEntity.setEndedEpochMilliseconds(ENDED_EPOCH_MILLIS);
        gameEntity.setVictoryPointGoal(10);

        List<PlayerEntity> players = new ArrayList<>();
        for (int i = 0; i < playerCount; i++) {
            var playerEntity = new PlayerEntity();
            playerEntity.setGame(gameEntity);
            playerEntity.setUser(new UserEntity(gameName + "-user" + i, "player" + i));
            players.add(playerEntity);
        }
        return players;
    }
}
