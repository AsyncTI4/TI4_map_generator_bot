package ti4.game;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import ti4.helpers.Constants;

class GameTest {

    @Test
    void getActionPhaseTurnOrder() {
        var game = createThreePlayerGame();
        assertThat(game.getActionPhaseTurnOrder("hasThe2")).isEqualTo(2);
        assertThat(game.getActionPhaseTurnOrder("hasThe1")).isEqualTo(1);
        assertThat(game.getActionPhaseTurnOrder("naaluPnPlayer")).isEqualTo(0);
        assertThat(game.getActionPhaseTurnOrder("doesNotExist")).isEqualTo(-1);
    }

    @Test
    void shouldTrackAcPlaysWithTargets() {
        var game = new Game();
        game.setStoredValue("unrelated", "value");

        game.getGameStats().recordAcPlayWithTarget(GameStats.OVERRULE, "leadership");
        game.getGameStats().recordAcPlayWithTarget(GameStats.OVERRULE, "leadership");
        game.getGameStats().recordAcPlayWithTarget(GameStats.OVERRULE, "politics");
        game.getGameStats().recordAcPlayWithTarget(GameStats.SABOTAGE, "Divert Funding");

        assertThat(game.getGameStats().getCountPerTarget(GameStats.OVERRULE))
                .containsExactlyInAnyOrderEntriesOf(Map.of("leadership", 2, "politics", 1));
        assertThat(game.getGameStats().getTotalPlays(GameStats.OVERRULE)).isEqualTo(3);
        assertThat(game.getGameStats().getCountPerTarget(GameStats.SABOTAGE))
                .containsExactlyInAnyOrderEntriesOf(Map.of("Divert Funding", 1));
        assertThat(game.getGameStats().getTotalPlays(GameStats.SABOTAGE)).isEqualTo(1);
        assertThat(game.getStoredValueMap()).containsOnlyKeys("unrelated");
    }

    private Game createThreePlayerGame() {
        var game = new Game();
        game.setName("threePlayerGame");
        var naaluPnPlayer = createPlayer("naaluPnPlayer", Set.of(7, 3), game);
        naaluPnPlayer.addPromissoryNoteToPlayArea(Constants.NAALU_PN);
        game.setPlayers(Map.of(
                "hasThe2", createPlayer("hasThe2", Set.of(2, 5), game),
                "hasThe1", createPlayer("hasThe1", Set.of(8, 1), game),
                "naaluPnPlayer", naaluPnPlayer));
        return game;
    }

    private Player createPlayer(String userId, Set<Integer> strategyCards, Game game) {
        var player = new Player(userId, "", game);
        player.setSCs(strategyCards);
        return player;
    }
}
