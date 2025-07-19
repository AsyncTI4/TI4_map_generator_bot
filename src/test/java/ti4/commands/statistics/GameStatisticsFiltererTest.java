package ti4.commands.statistics;

import java.util.Map;
import java.util.function.Predicate;

import org.junit.jupiter.api.Test;

import ti4.map.Game;
import ti4.map.Player;
import ti4.testUtils.BaseTi4Test;

import static org.assertj.core.api.Assertions.assertThat;

class GameStatisticsFiltererTest extends BaseTi4Test {
    @Test
    void filterRemovesGamesWithTwoRoundsOrLess() {
        Game round1 = createGame(1);
        Game round2 = createGame(2);
        Game round3 = createGame(3);

        Predicate<Game> filter = GameStatisticsFilterer.getNormalFinishedGamesFilter(null, null);

        assertThat(filter.test(round1)).isFalse();
        assertThat(filter.test(round2)).isFalse();
        assertThat(filter.test(round3)).isTrue();
    }

    private Game createGame(int round) {
        Game game = new Game();
        game.setRound(round);
        game.setVp(0);
        game.setHasEnded(true);
        Player player = new Player("p1", "p1", game);
        game.setPlayers(Map.of("p1", player));
        return game;
    }
}
