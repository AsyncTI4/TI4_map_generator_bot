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
    void scorePublicObjectiveFailsWhenGameHasEnded() {
        var game = new Game();
        var player = createPlayer("player1", Set.of(), game);
        game.setPlayers(Map.of("player1", player));
        Integer publicObjectiveId = game.addCustomPO("Test Objective", 1);
        game.setHasEnded(true);

        boolean scored = game.scorePublicObjective(player.getUserID(), publicObjectiveId);

        assertThat(scored).isFalse();
        assertThat(game.getScoredPublicObjectives()).isEmpty();
    }

    @Test
    void scoreSecretObjectiveFailsWhenGameHasEnded() {
        var game = new Game();
        var player = createPlayer("player1", Set.of(), game);
        game.setPlayers(Map.of("player1", player));
        player.setSecret("test_secret_objective", 42);
        game.setHasEnded(true);

        boolean scored = game.scoreSecretObjective(player.getUserID(), 42);

        assertThat(scored).isFalse();
        assertThat(player.getSecrets()).containsEntry("test_secret_objective", 42);
        assertThat(player.getSecretsScored()).isEmpty();
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
