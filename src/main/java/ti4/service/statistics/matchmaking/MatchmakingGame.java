package ti4.service.statistics.matchmaking;

import java.util.Comparator;
import java.util.List;
import ti4.map.Game;

record MatchmakingGame(String name, long endedDate, List<MatchmakingPlayer> players) {

    static MatchmakingGame from(Game game) {
        return new MatchmakingGame(game.getName(), game.getEndedDate(), getPlayers(game));
    }

    private static List<MatchmakingPlayer> getPlayers(Game game) {
        return game.getRealAndEliminatedPlayers().stream()
                .map(player -> {
                    boolean isWinner = game.getWinners().stream()
                            .anyMatch(gamePlayer -> gamePlayer.getUserID().equals(player.getUserID()));
                    int rank = isWinner ? 1 : game.getVp() - player.getTotalVictoryPoints() <= 3 ? 2 : 3;
                    return new MatchmakingPlayer(player.getUserID(), rank);
                })
                // it is recommended to always pass in sorted data when ties can occur
                .sorted(Comparator.comparing(MatchmakingPlayer::userId))
                .toList();
    }
}
