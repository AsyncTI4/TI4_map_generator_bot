package ti4.game;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import ti4.helpers.Constants;
import ti4.json.JsonMapperManager;

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
    void shouldTrackAcPlaysWithPlayersAndOptionalTargets() {
        var game = new Game();
        game.setStoredValue("unrelated", "value");
        var player = createPlayer("player1", Set.of(), game);

        game.getGameStats().recordAcPlayWithTarget(GameStats.OVERRULE, player, "leadership");
        game.getGameStats().recordAcPlayWithTarget(GameStats.OVERRULE, player, "leadership");
        game.getGameStats().recordAcPlayWithTarget(GameStats.OVERRULE, player, "politics");
        game.getGameStats().recordAcPlay(GameStats.SABOTAGE, player);

        assertThat(game.getGameStats().getCountPerTarget(GameStats.OVERRULE))
                .containsExactlyInAnyOrderEntriesOf(Map.of("leadership", 2, "politics", 1));
        assertThat(game.getGameStats().getTotalPlays(GameStats.OVERRULE)).isEqualTo(3);
        assertThat(game.getGameStats().getCountPerTarget(GameStats.SABOTAGE)).isEmpty();
        assertThat(game.getGameStats().getTotalPlays(GameStats.SABOTAGE)).isEqualTo(1);
        assertThat(game.getGameStats().getActionCardPlays())
                .extracting(GameStats.ActionCardPlay::getPlayerId)
                .containsOnly("player1");
        assertThat(game.getStoredValueMap()).containsOnlyKeys("unrelated");
    }

    @Test
    void shouldOnlySerializeNonEmptyTargetsForActionCardPlays() {
        var game = new Game();
        var player = createPlayer("player1", Set.of(), game);

        game.getGameStats().recordAcPlayWithTarget(GameStats.OVERRULE, player, "leadership");
        game.getGameStats().recordAcPlay(GameStats.SABOTAGE, player);

        var json = JsonMapperManager.basic().valueToTree(game.getGameStats()).get("actionCardPlays");
        assertThat(json.get(0).get("target").asText()).isEqualTo("leadership");
        assertThat(json.get(1).has("target")).isFalse();
    }

    @Test
    void shouldTrackAllPeekablePublicObjectivesForOracle() {
        var game = new Game();
        var player = createPlayer("player1", Set.of(), game);
        game.setPublicObjectives1Peekable(new java.util.ArrayList<>(java.util.List.of("po1a", "po1b")));
        game.setPublicObjectives2Peekable(new java.util.ArrayList<>(java.util.List.of("po2a")));

        game.peekAtAllUnrevealedPublicObjectives(player);
        game.peekAtAllUnrevealedPublicObjectives(player);

        assertThat(game.getPublicObjectives1Peeked())
                .containsEntry("po1a", java.util.List.of("player1"))
                .containsEntry("po1b", java.util.List.of("player1"));
        assertThat(game.getPublicObjectives2Peeked()).containsEntry("po2a", java.util.List.of("player1"));
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
