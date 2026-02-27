package ti4.spring.service.statistics.matchmaking;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import ti4.spring.service.persistence.GameEntity;
import ti4.spring.service.persistence.PlayerEntity;

record MatchmakingGame(String name, long endedDate, List<MatchmakingPlayer> players) {

    static List<MatchmakingGame> getMatchmakingGames(Iterable<PlayerEntity> players) {
        Map<GameEntity, List<PlayerEntity>> gamePlayers = new LinkedHashMap<>();
        for (PlayerEntity player : players) {
            gamePlayers
                    .computeIfAbsent(player.getGame(), game -> new ArrayList<>())
                    .add(player);
        }

        return gamePlayers.entrySet().stream()
                .map(entry -> toMatchmakingGame(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());
    }

    private static MatchmakingGame toMatchmakingGame(GameEntity game, List<PlayerEntity> players) {
        List<MatchmakingPlayer> matchmakingPlayers = players.stream()
                .map(player -> {
                    int rank = calculatePlayerRank(player.isWinner(), game.getVictoryPointGoal(), player.getScore());
                    return new MatchmakingPlayer(
                            player.getUser().getId(), player.getUser().getName(), rank);
                })
                .toList();
        long endedDate = game.getEndedEpochMilliseconds();
        return new MatchmakingGame(game.getGameName(), endedDate, matchmakingPlayers);
    }

    private static int calculatePlayerRank(boolean isWinner, int gameVictoryPointGoal, int playerScore) {
        if (isWinner) {
            return 1;
        }
        int pointsAwayFromVictory = gameVictoryPointGoal - playerScore;
        if (pointsAwayFromVictory <= 3) {
            return 2;
        }
        return 3 + pointsAwayFromVictory;
    }
}
