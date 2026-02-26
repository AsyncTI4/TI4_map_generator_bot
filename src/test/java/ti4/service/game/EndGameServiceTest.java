package ti4.service.game;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import ti4.map.Game;
import ti4.map.Player;
import ti4.testUtils.BaseTi4Test;

class EndGameServiceTest extends BaseTi4Test {

    @Test
    void shouldSuppressPublishWhenRoundIsOne() {
        Game game = new Game();
        game.setRound(1);
        assertThat(EndGameService.shouldSuppressPublish(game)).isTrue();
    }

    @Test
    void shouldSuppressPublishWhenRoundIsOneEvenWithRealPlayers() {
        Game game = createGameWithRealPlayers(1);
        assertThat(EndGameService.shouldSuppressPublish(game)).isTrue();
    }

    @Test
    void shouldSuppressPublishWhenNoRealPlayers() {
        Game game = new Game();
        game.setRound(3);
        assertThat(EndGameService.shouldSuppressPublish(game)).isTrue();
    }

    @Test
    void shouldNotSuppressPublishWhenRealPlayersExistAndRoundIsGreaterThanOne() {
        Game game = createGameWithRealPlayers(2);
        assertThat(EndGameService.shouldSuppressPublish(game)).isFalse();
    }

    @Test
    void shouldNotSuppressPublishForHigherRounds() {
        Game game = createGameWithRealPlayers(5);
        assertThat(EndGameService.shouldSuppressPublish(game)).isFalse();
    }

    private static Game createGameWithRealPlayers(int round) {
        Game game = new Game();
        game.setRound(round);
        Player player = new Player("user1", "testuser", game);
        player.setFaction("arborec");
        player.setColor("red");
        game.setPlayers(Map.of("user1", player));
        return game;
    }
}
