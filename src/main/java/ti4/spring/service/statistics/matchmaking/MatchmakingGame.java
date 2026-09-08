package ti4.spring.service.statistics.matchmaking;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import ti4.spring.service.persistence.GameEntity;
import ti4.spring.service.persistence.PlayerEntity;

record MatchmakingGame(String name, long endedDate, List<MatchmakingPlayer> players) {

    private static final int UNRANKED_FOR_REPORTING = 0;

    static List<MatchmakingGame> getMatchmakingGames(
            Iterable<PlayerEntity> players, Function<String, Map<String, Integer>> simulatedRanksByGameName) {
        Map<GameEntity, List<PlayerEntity>> gamePlayers = new LinkedHashMap<>();
        for (PlayerEntity player : players) {
            gamePlayers
                    .computeIfAbsent(player.getGame(), _ -> new ArrayList<>())
                    .add(player);
        }

        return gamePlayers.entrySet().stream()
                .map(entry -> toMatchmakingGame(
                        entry.getKey(),
                        entry.getValue(),
                        simulatedRanksByGameName.apply(entry.getKey().getGameName())))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    static List<MatchmakingGame> getGamesForReporting(Iterable<PlayerEntity> players) {
        Map<GameEntity, List<PlayerEntity>> gamePlayers = new LinkedHashMap<>();
        for (PlayerEntity player : players) {
            gamePlayers
                    .computeIfAbsent(player.getGame(), _ -> new ArrayList<>())
                    .add(player);
        }

        return gamePlayers.entrySet().stream()
                .map(entry -> new MatchmakingGame(
                        entry.getKey().getGameName(),
                        entry.getKey().getEndedEpochMilliseconds(),
                        entry.getValue().stream()
                                .map(player -> new MatchmakingPlayer(
                                        player.getUser().getId(),
                                        player.getUser().getName(),
                                        UNRANKED_FOR_REPORTING))
                                .toList()))
                .collect(Collectors.toList());
    }

    private static MatchmakingGame toMatchmakingGame(
            GameEntity game, List<PlayerEntity> players, Map<String, Integer> simulatedRanks) {
        if (simulatedRanks == null || simulatedRanks.isEmpty()) {
            return null;
        }
        List<MatchmakingPlayer> matchmakingPlayers = new ArrayList<>();
        for (PlayerEntity player : players) {
            Integer rank = simulatedRanks.get(player.getUser().getId());
            if (rank == null) {
                return null;
            }
            matchmakingPlayers.add(new MatchmakingPlayer(
                    player.getUser().getId(), player.getUser().getName(), rank));
        }
        long endedDate = game.getEndedEpochMilliseconds();
        return new MatchmakingGame(game.getGameName(), endedDate, matchmakingPlayers);
    }
}
