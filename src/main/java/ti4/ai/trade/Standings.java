package ti4.ai.trade;

import java.util.List;
import java.util.Objects;
import lombok.experimental.UtilityClass;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
class Standings {

    static int vp(Player player) {
        return player.getTotalVictoryPoints();
    }

    static int goal(Game game) {
        return game.getVp();
    }

    static boolean same(Player first, Player second) {
        return first != null && second != null && Objects.equals(first.getFaction(), second.getFaction());
    }

    static List<Player> players(Game game) {
        return game.getRealPlayers().stream()
                .filter(player -> !player.isEliminated())
                .toList();
    }

    static List<Player> others(Game game, Player player) {
        return players(game).stream().filter(other -> !same(other, player)).toList();
    }

    static boolean anyoneAtLeast(Game game, int points) {
        return players(game).stream().anyMatch(player -> vp(player) >= points);
    }
}
