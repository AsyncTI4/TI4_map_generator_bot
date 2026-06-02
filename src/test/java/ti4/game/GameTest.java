package ti4.game;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import ti4.helpers.Constants;
import ti4.json.JsonMapperManager;
import ti4.testUtils.BaseTi4Test;

class GameTest extends BaseTi4Test {

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
        game.setPublicObjectives1Peekable(new ArrayList<>(List.of("po1a", "po1b")));
        game.setPublicObjectives2Peekable(new ArrayList<>(List.of("po2a")));

        game.peekAtAllUnrevealedPublicObjectives(player);
        game.peekAtAllUnrevealedPublicObjectives(player);

        assertThat(game.getPublicObjectives1Peeked())
                .containsEntry("po1a", List.of("player1"))
                .containsEntry("po1b", List.of("player1"));
        assertThat(game.getPublicObjectives2Peeked()).containsEntry("po2a", List.of("player1"));
    }

    @Test
    void shouldUnrevealStageOnePublicObjectiveAsPeekedByAllPlayers() {
        var game = createThreePlayerGame();
        game.setPublicObjectives1(new ArrayList<>());
        game.setPublicObjectives2(new ArrayList<>());
        game.setPublicObjectives1Peekable(new ArrayList<>(List.of("develop")));
        game.setRevealedPublicObjectives(new LinkedHashMap<>(Map.of("corner", 0)));

        assertThat(game.unrevealPublicObjective(0)).isTrue();

        assertThat(game.getRevealedPublicObjectives()).doesNotContainKey("corner");
        assertThat(game.getPublicObjectives1()).doesNotContain("corner");
        assertThat(game.getPublicObjectives1Peekable()).containsExactly("corner", "develop");
        assertThat(game.getPublicObjectives1Peeked())
                .containsEntry("corner", List.of("hasThe2", "hasThe1", "naaluPnPlayer"));
    }

    @Test
    void shouldUnrevealStageTwoPublicObjectiveAsPeekedByAllPlayers() {
        var game = createThreePlayerGame();
        game.setPublicObjectives1(new ArrayList<>());
        game.setPublicObjectives2(new ArrayList<>());
        game.setPublicObjectives2Peekable(new ArrayList<>(List.of("galvanize")));
        game.setRevealedPublicObjectives(new LinkedHashMap<>(Map.of("centralize_trade", 1)));

        assertThat(game.unrevealPublicObjective(1)).isTrue();

        assertThat(game.getRevealedPublicObjectives()).doesNotContainKey("centralize_trade");
        assertThat(game.getPublicObjectives2()).doesNotContain("centralize_trade");
        assertThat(game.getPublicObjectives2Peekable()).containsExactly("centralize_trade", "galvanize");
        assertThat(game.getPublicObjectives2Peeked())
                .containsEntry("centralize_trade", List.of("hasThe2", "hasThe1", "naaluPnPlayer"));
    }

    private Game createThreePlayerGame() {
        var game = new Game();
        game.setName("threePlayerGame");
        var naaluPnPlayer = createPlayer("naaluPnPlayer", Set.of(7, 3), game);
        naaluPnPlayer.setFaction("naalu");
        naaluPnPlayer.setColor("blue");
        naaluPnPlayer.addPromissoryNoteToPlayArea(Constants.NAALU_PN);
        var hasThe2 = createPlayer("hasThe2", Set.of(2, 5), game);
        hasThe2.setFaction("arborec");
        hasThe2.setColor("green");
        var hasThe1 = createPlayer("hasThe1", Set.of(8, 1), game);
        hasThe1.setFaction("jolnar");
        hasThe1.setColor("red");

        var players = new LinkedHashMap<String, Player>();
        players.put("hasThe2", hasThe2);
        players.put("hasThe1", hasThe1);
        players.put("naaluPnPlayer", naaluPnPlayer);
        game.setPlayers(players);
        return game;
    }

    private Player createPlayer(String userId, Set<Integer> strategyCards, Game game) {
        var player = new Player(userId, "", game);
        player.setSCs(strategyCards);
        return player;
    }
}
