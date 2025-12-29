package ti4.service.statistics.matchmaking;

import java.util.List;
import ti4.map.Game;
import ti4.map.Player;

record MatchmakingGame(String name, long endedDate, List<MatchmakingPlayer> players) {

    static MatchmakingGame from(Game game) {
        return new MatchmakingGame(game.getName(), game.getEndedDate(), getPlayers(game));
    }

    private static List<MatchmakingPlayer> getPlayers(Game game) {
        return game.getRealAndEliminatedPlayers().stream()
                .map(player -> {
                    String userId = player.getStatsTrackedUserID();
                    String username = player.getStatsTrackedUserName();
                    int rank =
                            calculatePlayerRank(isWinner(game, player), game.getVp(), player.getTotalVictoryPoints());
                    return new MatchmakingPlayer(userId, username, rank);
                })
                .toList();
    }

    private static boolean isWinner(Game game, Player player) {
        return game.getWinners().contains(player);
    }

    private static int calculatePlayerRank(boolean isWinner, int gameVictoryPoints, int playerVictoryPoints) {
        if (isWinner) {
            return 1;
        }
        int pointsAwayFromVictory = gameVictoryPoints - playerVictoryPoints;
        if (pointsAwayFromVictory <= 3) {
            return 2;
        }
        return 3 + pointsAwayFromVictory;
    }
}
