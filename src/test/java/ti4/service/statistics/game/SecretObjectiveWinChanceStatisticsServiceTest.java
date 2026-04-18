package ti4.service.statistics.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;
import ti4.testUtils.BaseTi4Test;

class SecretObjectiveWinChanceStatisticsServiceTest extends BaseTi4Test {

    @Test
    void collectSecretObjectiveWinChanceStatsTracksScoredAndUnscoredPhaseCombinations() {
        Game game = new Game();
        game.setName("secret-phase-combo");
        game.setVp(1);
        game.setHasEnded(true);

        Player winner = game.addPlayer("winner", "Winner");
        winner.setFaction("arborec");
        winner.setColor("red");
        winner.setSecretScored("accept_bribes_pbd100");
        winner.setSecret("rule_a_diverse_empire_pbd100");
        winner.setSecret("deep_space_research_pbd100");

        Player loser = game.addPlayer("loser", "Loser");
        loser.setFaction("hacan");
        loser.setColor("blue");
        loser.setSecret("gather_a_legion_pbd100");
        loser.setSecret("sponsor_data_archives_pbd100");
        loser.setSecret("secure_a_path_pbd100");

        int[] playersByScoredAPSecretCount = new int[5];
        int[] winsByScoredAPSecretCount = new int[5];
        int[] playersByScoredSecretCount = new int[5];
        int[] winsByScoredSecretCount = new int[5];
        Map<String, Integer> playersBySecretPhaseCombination = new HashMap<>();
        Map<String, Integer> winsBySecretPhaseCombination = new HashMap<>();
        Map<String, Integer> gamesWithSecretScored = new HashMap<>();
        Map<String, Integer> winsWithSecretScored = new HashMap<>();
        Map<String, Integer> gamesWithSecretInHand = new HashMap<>();
        Map<String, Integer> winsWithSecretInHand = new HashMap<>();
        Map<String, Integer> gamesWithSecretScoredOrInHand = new HashMap<>();
        Map<String, Integer> winsWithSecretScoredOrInHand = new HashMap<>();

        SecretObjectiveWinChanceStatisticsService.collectSecretObjectiveWinChanceStats(
                game,
                playersByScoredAPSecretCount,
                winsByScoredAPSecretCount,
                playersByScoredSecretCount,
                winsByScoredSecretCount,
                playersBySecretPhaseCombination,
                winsBySecretPhaseCombination,
                gamesWithSecretScored,
                winsWithSecretScored,
                gamesWithSecretInHand,
                winsWithSecretInHand,
                gamesWithSecretScoredOrInHand,
                winsWithSecretScoredOrInHand);

        assertEquals(1, playersBySecretPhaseCombination.get("1|2"));
        assertEquals(1, winsBySecretPhaseCombination.get("1|2"));
        assertEquals(1, playersBySecretPhaseCombination.get("0|3"));
        assertEquals(0, winsBySecretPhaseCombination.getOrDefault("0|3", 0));
    }

    @Test
    void buildSecretPhaseCombinationWinChanceSectionFormatsAndSortsCombinations() {
        Map<String, Integer> playersBySecretPhaseCombination = Map.of("1|2", 2, "0|3", 1, "2|0", 1);
        Map<String, Integer> winsBySecretPhaseCombination = Map.of("1|2", 1, "2|0", 1);

        String report = SecretObjectiveWinChanceStatisticsService.buildSecretPhaseCombinationWinChanceSection(
                playersBySecretPhaseCombination, winsBySecretPhaseCombination);

        assertTrue(report.contains("__**Scored + Unscored Secret Phase Combination Win Chance**__"));
        assertTrue(report.contains("`2 actions` `100%` (1/1)"));
        assertTrue(report.contains("`3 statuses` `  0%` (0/1)"));
        assertTrue(report.contains("`1 action and 2 statuses` ` 50%` (1/2)"));
        assertTrue(report.indexOf("`2 actions`") < report.indexOf("`3 statuses`"));
        assertTrue(report.indexOf("`3 statuses`") < report.indexOf("`1 action and 2 statuses`"));
    }
}
