package ti4.service.statistics.matchmaking;

import java.util.Comparator;
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
                    boolean isWinner = isWinner(game, player);
                    int rank = calculatePlayerRank(isWinner, game.getVp(), player.getTotalVictoryPoints());
                    return new MatchmakingPlayer(player.getUserID(), rank);
                })
                // it is recommended to always pass in sorted data when ties can occur
                .sorted(Comparator.comparing(MatchmakingPlayer::userId))
                .toList();
    }

    private static boolean isWinner(Game game, Player player) {
        return game.getWinners().stream()
                .anyMatch(gamePlayer -> gamePlayer.getUserID().equals(player.getUserID()));
    }

    private static int calculatePlayerRank(boolean isWinner, int gameVictoryPoints, int playerVictoryPoints) {
        if (isWinner) {
            return 1;
        }
        if (gameVictoryPoints - playerVictoryPoints <= 3) {
            return 2;
        }
        return 3;
    }
}
