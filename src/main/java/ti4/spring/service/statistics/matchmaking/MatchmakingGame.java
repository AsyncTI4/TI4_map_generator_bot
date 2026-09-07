package ti4.spring.service.statistics.matchmaking;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import ti4.spring.service.persistence.GameEntity;
import ti4.spring.service.persistence.PlayerEntity;

record MatchmakingGame(String name, long endedDate, List<MatchmakingPlayer> players) {

    static List<MatchmakingGame> getMatchmakingGames(Iterable<PlayerEntity> players) {
        Map<GameEntity, List<PlayerEntity>> gamePlayers = new LinkedHashMap<>();
        for (PlayerEntity player : players) {
            gamePlayers
                    .computeIfAbsent(player.getGame(), _ -> new ArrayList<>())
                    .add(player);
        }

        return gamePlayers.entrySet().stream()
                .map(entry -> toMatchmakingGame(entry.getKey(), entry.getValue()))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private static MatchmakingGame toMatchmakingGame(GameEntity game, List<PlayerEntity> players) {
        if (players.stream().anyMatch(player -> player.getSimulatedRank() == null)) {
            return null;
        }
        List<MatchmakingPlayer> matchmakingPlayers = players.stream()
                .map(player -> new MatchmakingPlayer(
                        player.getUser().getId(), player.getUser().getName(), player.getSimulatedRank()))
                .toList();
        long endedDate = game.getEndedEpochMilliseconds();
        return new MatchmakingGame(game.getGameName(), endedDate, matchmakingPlayers);
    }
}
