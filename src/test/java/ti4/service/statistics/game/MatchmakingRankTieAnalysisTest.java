package ti4.service.statistics.game;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;
import ti4.testUtils.BaseTi4Test;

class MatchmakingRankTieAnalysisTest extends BaseTi4Test {

    private static final int VICTORY_POINT_GOAL = 10;
    private static final List<String> SCORING_SECRETS =
            List.of("pem", "otf", "mtm", "hrm", "eh", "dhw", "dfat", "te", "ose", "mrm");
    private static final List<String> FACTIONS = List.of("sol", "hacan", "letnev", "xxcha");
    private static final List<String> COLORS = List.of("blue", "red", "green", "yellow");

    @Test
    void reportsATieAndTheMetricsThatWouldSeparateIt() {
        Game game = agendaEndedGame();
        addPlayer(game, "winner", 1, VICTORY_POINT_GOAL);
        Player firstTied = addPlayer(game, "tiedA", 2, 6);
        Player secondTied = addPlayer(game, "tiedB", 3, 6);
        addPlayer(game, "trailing", 4, 2);
        firstTied.addPlanet("mr");

        var analysis = new MatchmakingRankTieAnalysis(5);
        analysis.consume(game);
        String summary = analysis.summary();

        assertThat(summary).contains("ranked games: 1");
        assertThat(summary).contains("games with at least one tie: 1");
        assertThat(summary).contains("tied pairs: 1");
        assertThat(summary).contains("tie group sizes: {2=1}");
        assertThat(summary).contains("planets: 1 (100.0%)");
        assertThat(summary).contains("victoryPoints: 0 (0.0%)");
        assertThat(summary).contains("any board metric except initiative: 1 (100.0%)");
        assertThat(analysis.getSamples()).hasSize(1);
        assertThat(analysis.getSamples().getFirst()).contains("simulatedTotal=6");
    }

    @Test
    void reportsNothingForAGameWithoutTies() {
        Game game = agendaEndedGame();
        addPlayer(game, "winner", 1, VICTORY_POINT_GOAL);
        addPlayer(game, "second", 2, 7);
        addPlayer(game, "third", 3, 4);

        var analysis = new MatchmakingRankTieAnalysis(5);
        analysis.consume(game);

        assertThat(analysis.summary()).contains("games with at least one tie: 0");
        assertThat(analysis.summary()).contains("tied pairs: 0");
        assertThat(analysis.getSamples()).isEmpty();
    }

    @Test
    void ignoresGamesThatCannotBeRanked() {
        Game game = agendaEndedGame();
        addPlayer(game, "first", 1, 4);
        addPlayer(game, "second", 2, 4);

        var analysis = new MatchmakingRankTieAnalysis(5);
        analysis.consume(game);

        assertThat(analysis.summary()).contains("ranked games: 0");
    }

    private static Game agendaEndedGame() {
        Game game = new Game();
        game.setName("tie-analysis-" + UUID.randomUUID());
        game.newGameSetup();
        game.setVp(VICTORY_POINT_GOAL);
        game.setHasEnded(true);
        game.setPhaseOfGame("agendaEnd");
        return game;
    }

    private static Player addPlayer(Game game, String userId, int strategyCard, int victoryPoints) {
        int seat = game.getPlayers().size();
        Player player = game.addPlayer(userId, userId);
        player.setFaction(FACTIONS.get(seat % FACTIONS.size()));
        player.setColor(COLORS.get(seat % COLORS.size()));
        player.setSCs(Set.of(strategyCard));
        for (int i = 0; i < victoryPoints; i++) {
            player.setSecretScored(SCORING_SECRETS.get(i));
        }
        return player;
    }
}
