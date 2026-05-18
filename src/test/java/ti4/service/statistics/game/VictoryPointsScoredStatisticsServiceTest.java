package ti4.service.statistics.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;
import ti4.testUtils.BaseTi4Test;

class VictoryPointsScoredStatisticsServiceTest extends BaseTi4Test {

    @Test
    void collectScoredSecretsMatchesWinChanceTrackedSecretPopulation() {
        Game game = new Game();
        game.setName("scored-secret-alignment");
        game.setVp(1);
        game.setHasEnded(true);
        game.addToSoToPoList("accept_bribes_pbd100");

        Player winner = game.addPlayer("winner", "Winner");
        winner.setFaction("arborec");
        winner.setColor("red");
        winner.setSecretScored("rule_a_diverse_empire_pbd100");
        winner.setSecretScored("accept_bribes_pbd100");
        winner.setSecretScored("fake_plotted_secret");

        Player eliminated = game.addPlayer("eliminated", "Eliminated");
        eliminated.setFaction("hacan");
        eliminated.setColor("blue");
        eliminated.setEliminated(true);
        eliminated.setSecretScored("mine_rare_metals_pbd100");

        Map<String, Integer> scoredCounts = new HashMap<>();
        VictoryPointsScoredStatisticsService.collectScoredSecrets(game, scoredCounts);

        Map<String, Integer> gamesWithSecretScored = new HashMap<>();
        SecretObjectiveWinChanceStatisticsService.collectSecretObjectiveWinChanceStats(
                game,
                new int[5],
                new int[5],
                new int[5],
                new int[5],
                new int[5][5],
                new int[5][5],
                new HashMap<>(),
                new HashMap<>(),
                gamesWithSecretScored,
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>());

        assertEquals(gamesWithSecretScored, scoredCounts);
        assertEquals(1, scoredCounts.get("Rule a Diverse Empire"));
        assertEquals(1, scoredCounts.get("Mine Rare Metals"));
        assertFalse(scoredCounts.containsKey("Accept Bribes"));
    }

    @Test
    void collectScoredSecretsIgnoresGamesRejectedBySecretWinChanceStats() {
        Game game = new Game();
        game.setName("ignored-secret-score-game");
        game.setVp(1);
        game.setHasEnded(true);

        Player winner = game.addPlayer("winner", "Winner");
        winner.setFaction("jolnar");
        winner.setColor("yellow");
        winner.setSecretScored("accept_bribes_pbd100");
        winner.setSecret("rule_a_diverse_empire_pbd100");
        winner.setSecret("deep_space_research_pbd100");
        winner.setSecret("monopolize_production_pbd100");
        winner.setSecret("sponsor_data_archives_pbd100");

        Map<String, Integer> scoredCounts = new HashMap<>();
        VictoryPointsScoredStatisticsService.collectScoredSecrets(game, scoredCounts);

        assertEquals(Map.of(), scoredCounts);
    }
}
