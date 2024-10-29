package ti4.map;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class GameTest {

    @Test
    void getActionPhaseTurnOrder() {
        var game = new Game();
        game.setPlayers(Map.of(
                "test1", createPlayer("test1", Set.of(2, 5)),
                "test2", createPlayer("test2", Set.of(8, 1)),
                "test3", createPlayer("test3", Set.of(7, 3))
        ));

        assertThat(game.getActionPhaseTurnOrder("test1")).isEqualTo(1);
        assertThat(game.getActionPhaseTurnOrder("test2")).isEqualTo(0);
        assertThat(game.getActionPhaseTurnOrder("test3")).isEqualTo(2);
        assertThat(game.getActionPhaseTurnOrder("test4")).isEqualTo(-1);
    }

    private Player createPlayer(String userId, Set<Integer> strategyCards) {
        var player = new Player();
        player.setUserID(userId);
        player.setSCs(strategyCards);
        return player;
    }

}