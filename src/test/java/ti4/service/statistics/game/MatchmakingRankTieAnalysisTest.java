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
    private static final int SAMPLE_SIZE = 5;
    private static final List<String> SCORING_SECRETS =
            List.of("pem", "otf", "mtm", "hrm", "eh", "dhw", "dfat", "te", "ose", "mrm");
    private static final List<String> FACTIONS = List.of("sol", "hacan", "letnev", "xxcha", "arborec", "saar");
    private static final List<String> COLORS = List.of("blue", "red", "green", "yellow", "black", "purple");

    @Test
    void reportsATieAndTheMetricsThatWouldSeparateIt() {
        Game game = endedGame();
        addPlayer(game, "winner", 1, VICTORY_POINT_GOAL);
        Player tiedWithAPlanet = addPlayer(game, "tiedA", 2, 6);
        addPlayer(game, "tiedB", 3, 6);
        addPlayer(game, "fourth", 4, 4);
        addPlayer(game, "fifth", 5, 2);
        tiedWithAPlanet.addPlanet("mr");

        var analysis = new MatchmakingRankTieAnalysis(SAMPLE_SIZE);
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
        Game game = endedGame();
        addPlayer(game, "winner", 1, VICTORY_POINT_GOAL);
        addPlayer(game, "second", 2, 8);
        addPlayer(game, "third", 3, 6);
        addPlayer(game, "fourth", 4, 4);
        addPlayer(game, "fifth", 5, 2);

        var analysis = new MatchmakingRankTieAnalysis(SAMPLE_SIZE);
        analysis.consume(game);

        assertThat(analysis.summary()).contains("ranked games: 1");
        assertThat(analysis.summary()).contains("games with at least one tie: 0");
        assertThat(analysis.summary()).contains("tied pairs: 0");
        assertThat(analysis.getSamples()).isEmpty();
    }

    @Test
    void ignoresGamesNobodyWon() {
        Game game = endedGame();
        addPlayer(game, "first", 1, 4);
        addPlayer(game, "second", 2, 4);
        addPlayer(game, "third", 3, 3);
        addPlayer(game, "fourth", 4, 2);
        addPlayer(game, "fifth", 5, 1);

        var analysis = new MatchmakingRankTieAnalysis(SAMPLE_SIZE);
        analysis.consume(game);

        assertThat(analysis.summary()).contains("ranked games: 0");
        assertThat(analysis.summary()).contains("outside the rated 5-8 player corpus: 0");
    }

    @Test
    void ignoresGamesBelowTheRatedPlayerCount() {
        Game game = endedGame();
        addPlayer(game, "winner", 1, VICTORY_POINT_GOAL);
        addPlayer(game, "tiedA", 2, 6);
        addPlayer(game, "tiedB", 3, 6);
        addPlayer(game, "fourth", 4, 2);

        var analysis = new MatchmakingRankTieAnalysis(SAMPLE_SIZE);
        analysis.consume(game);

        assertThat(analysis.summary()).contains("ranked games: 0");
        assertThat(analysis.summary()).contains("outside the rated 5-8 player corpus: 1");
    }

    @Test
    void ignoresGamesAboveTheRatedPlayerCount() {
        Game game = endedGame();
        addPlayer(game, "winner", 1, VICTORY_POINT_GOAL);
        for (int seat = 1; seat <= 8; seat++) {
            addPlayer(game, "player" + seat, seat, 6);
        }

        var analysis = new MatchmakingRankTieAnalysis(SAMPLE_SIZE);
        analysis.consume(game);

        assertThat(analysis.summary()).contains("ranked games: 0");
        assertThat(analysis.summary()).contains("outside the rated 5-8 player corpus: 1");
    }

    @Test
    void ignoresAllianceGames() {
        Game game = endedGame();
        game.setAllianceMode(true);
        addPlayer(game, "winner", 1, VICTORY_POINT_GOAL);
        addPlayer(game, "tiedA", 2, 6);
        addPlayer(game, "tiedB", 3, 6);
        addPlayer(game, "fourth", 4, 4);
        addPlayer(game, "fifth", 5, 2);

        var analysis = new MatchmakingRankTieAnalysis(SAMPLE_SIZE);
        analysis.consume(game);

        assertThat(analysis.summary()).contains("ranked games: 0");
        assertThat(analysis.summary()).contains("outside the rated 5-8 player corpus: 1");
    }

    private static Game endedGame() {
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
