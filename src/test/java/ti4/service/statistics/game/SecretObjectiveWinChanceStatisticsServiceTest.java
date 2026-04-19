package ti4.service.statistics.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        winner.setSecret("win_the_people's_favor_pbd100");

        Player loser = game.addPlayer("loser", "Loser");
        loser.setFaction("hacan");
        loser.setColor("blue");
        loser.setSecret("gather_a_legion_pbd100");
        loser.setSecret("sponsor_data_archives_pbd100");
        loser.setSecret("win_the_people's_favor_pbd100");

        int[] playersByScoredAPSecretCount = new int[5];
        int[] winsByScoredAPSecretCount = new int[5];
        int[] playersByScoredSecretCount = new int[5];
        int[] winsByScoredSecretCount = new int[5];
        int[][] playersByExactScoredSecretCountAndMinimumAPCount = new int[5][5];
        int[][] winsByExactScoredSecretCountAndMinimumAPCount = new int[5][5];
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
                playersByExactScoredSecretCountAndMinimumAPCount,
                winsByExactScoredSecretCountAndMinimumAPCount,
                playersBySecretPhaseCombination,
                winsBySecretPhaseCombination,
                gamesWithSecretScored,
                winsWithSecretScored,
                gamesWithSecretInHand,
                winsWithSecretInHand,
                gamesWithSecretScoredOrInHand,
                winsWithSecretScoredOrInHand);

        assertEquals(1, playersBySecretPhaseCombination.get("1|1|1"));
        assertEquals(1, winsBySecretPhaseCombination.get("1|1|1"));
        assertEquals(1, playersBySecretPhaseCombination.get("0|2|1"));
        assertEquals(0, winsBySecretPhaseCombination.getOrDefault("0|2|1", 0));
        assertEquals(0, playersByExactScoredSecretCountAndMinimumAPCount[1][0]);
        assertEquals(0, winsByExactScoredSecretCountAndMinimumAPCount[1][0]);
        assertEquals(1, playersByExactScoredSecretCountAndMinimumAPCount[1][1]);
        assertEquals(1, winsByExactScoredSecretCountAndMinimumAPCount[1][1]);
    }

    @Test
    void buildSecretPhaseCombinationWinChanceSectionFormatsAndSortsCombinations() {
        Map<String, Integer> playersBySecretPhaseCombination = Map.of("1|1|1", 2, "0|2|1", 1, "2|0|0", 1);
        Map<String, Integer> winsBySecretPhaseCombination = Map.of("1|1|1", 1, "2|0|0", 1);

        String report = SecretObjectiveWinChanceStatisticsService.buildSecretPhaseCombinationWinChanceSection(
                playersBySecretPhaseCombination, winsBySecretPhaseCombination);

        assertTrue(report.contains("__**Scored + Unscored Secret Combination Win Chance**__"));
        assertTrue(report.contains("action/status/agenda secret mix"));
        assertTrue(report.contains("`2 actions` `100%` (1/1)"));
        assertTrue(report.contains("`2 statuses and 1 agenda` `  0%` (0/1)"));
        assertTrue(report.contains("`1 action and 1 status and 1 agenda` ` 50%` (1/2)"));
        assertTrue(report.indexOf("`2 actions`") < report.indexOf("`2 statuses and 1 agenda`"));
        assertTrue(
                report.indexOf("`2 statuses and 1 agenda`") < report.indexOf("`1 action and 1 status and 1 agenda`"));
    }

    @Test
    void buildConditionedActionPhaseWinChanceSectionFormatsRequestedTotalsAndExactApCounts() {
        int[][] playersByExactScoredSecretCountAndMinimumAPCount = new int[5][5];
        int[][] winsByExactScoredSecretCountAndMinimumAPCount = new int[5][5];
        playersByExactScoredSecretCountAndMinimumAPCount[1][0] = 4;
        winsByExactScoredSecretCountAndMinimumAPCount[1][0] = 1;
        playersByExactScoredSecretCountAndMinimumAPCount[1][1] = 2;
        winsByExactScoredSecretCountAndMinimumAPCount[1][1] = 1;
        playersByExactScoredSecretCountAndMinimumAPCount[2][0] = 3;
        winsByExactScoredSecretCountAndMinimumAPCount[2][0] = 1;
        playersByExactScoredSecretCountAndMinimumAPCount[2][1] = 2;
        winsByExactScoredSecretCountAndMinimumAPCount[2][1] = 1;
        playersByExactScoredSecretCountAndMinimumAPCount[2][2] = 1;
        winsByExactScoredSecretCountAndMinimumAPCount[2][2] = 1;
        playersByExactScoredSecretCountAndMinimumAPCount[3][0] = 4;
        winsByExactScoredSecretCountAndMinimumAPCount[3][0] = 1;
        playersByExactScoredSecretCountAndMinimumAPCount[3][1] = 2;
        winsByExactScoredSecretCountAndMinimumAPCount[3][1] = 1;
        playersByExactScoredSecretCountAndMinimumAPCount[3][2] = 1;
        winsByExactScoredSecretCountAndMinimumAPCount[3][2] = 1;
        playersByExactScoredSecretCountAndMinimumAPCount[3][3] = 1;
        winsByExactScoredSecretCountAndMinimumAPCount[3][3] = 1;
        playersByExactScoredSecretCountAndMinimumAPCount[4][0] = 6;
        winsByExactScoredSecretCountAndMinimumAPCount[4][0] = 3;
        playersByExactScoredSecretCountAndMinimumAPCount[4][1] = 3;
        winsByExactScoredSecretCountAndMinimumAPCount[4][1] = 2;
        playersByExactScoredSecretCountAndMinimumAPCount[4][2] = 2;
        winsByExactScoredSecretCountAndMinimumAPCount[4][2] = 1;
        playersByExactScoredSecretCountAndMinimumAPCount[4][3] = 1;
        winsByExactScoredSecretCountAndMinimumAPCount[4][3] = 1;
        playersByExactScoredSecretCountAndMinimumAPCount[4][4] = 1;
        winsByExactScoredSecretCountAndMinimumAPCount[4][4] = 1;

        String report = SecretObjectiveWinChanceStatisticsService.buildConditionedActionPhaseWinChanceSection(
                playersByExactScoredSecretCountAndMinimumAPCount, winsByExactScoredSecretCountAndMinimumAPCount);

        assertTrue(report.contains("__**Action Phase Secret Win Chance by Total Scored Secrets**__"));
        assertTrue(report.contains("exactly Y action phase secrets"));
        assertTrue(report.contains("**1 total scored secret**"));
        assertTrue(report.contains("**2 total scored secrets**"));
        assertTrue(report.contains("**3 total scored secrets**"));
        assertTrue(report.contains("**4 total scored secrets**"));
        assertTrue(report.contains("`0 AP` ` 25%` (1/4)"));
        assertTrue(report.contains("`1 AP` ` 50%` (1/2)"));
        assertTrue(report.contains("`0 AP` ` 33%` (1/3)"));
        assertTrue(report.contains("`1 AP` ` 50%` (1/2)"));
        assertTrue(report.contains("`2 AP` `100%` (1/1)"));
        assertTrue(report.contains("`0 AP` ` 25%` (1/4)"));
        assertTrue(report.contains("`1 AP` ` 50%` (1/2)"));
        assertTrue(report.contains("`2 AP` `100%` (1/1)"));
        assertTrue(report.contains("`3 AP` `100%` (1/1)"));
        assertTrue(report.contains("`0 AP` ` 50%` (3/6)"));
        assertTrue(report.contains("`1 AP` ` 67%` (2/3)"));
        assertTrue(report.contains("`2 AP` ` 50%` (1/2)"));
        assertTrue(report.contains("`3 AP` `100%` (1/1)"));
        assertTrue(report.contains("`4 AP` `100%` (1/1)"));
    }

    @Test
    void collectSecretObjectiveWinChanceStatsIgnoresFakeScoredSecretsWhenBucketingTotals() {
        Game game = new Game();
        game.setName("fake-scored-secret");
        game.setVp(1);
        game.setHasEnded(true);

        Player winner = game.addPlayer("winner", "Winner");
        winner.setFaction("obsidian");
        winner.setColor("red");
        winner.setSecretScored("accept_bribes_pbd100");
        winner.setSecretScored("fake_plotted_secret");

        int[] playersByScoredAPSecretCount = new int[5];
        int[] winsByScoredAPSecretCount = new int[5];
        int[] playersByScoredSecretCount = new int[5];
        int[] winsByScoredSecretCount = new int[5];
        int[][] playersByExactScoredSecretCountAndMinimumAPCount = new int[5][5];
        int[][] winsByExactScoredSecretCountAndMinimumAPCount = new int[5][5];

        SecretObjectiveWinChanceStatisticsService.collectSecretObjectiveWinChanceStats(
                game,
                playersByScoredAPSecretCount,
                winsByScoredAPSecretCount,
                playersByScoredSecretCount,
                winsByScoredSecretCount,
                playersByExactScoredSecretCountAndMinimumAPCount,
                winsByExactScoredSecretCountAndMinimumAPCount,
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>());

        assertEquals(0, playersByScoredSecretCount[2]);
        assertEquals(0, winsByScoredSecretCount[2]);
        assertEquals(1, playersByScoredSecretCount[1]);
        assertEquals(1, winsByScoredSecretCount[1]);
        assertEquals(0, playersByExactScoredSecretCountAndMinimumAPCount[1][0]);
        assertEquals(0, winsByExactScoredSecretCountAndMinimumAPCount[1][0]);
        assertEquals(1, playersByExactScoredSecretCountAndMinimumAPCount[1][1]);
        assertEquals(1, winsByExactScoredSecretCountAndMinimumAPCount[1][1]);
    }

    @Test
    void collectSecretObjectiveWinChanceStatsIgnoresSecretsConvertedToPublicObjectives() {
        Game game = new Game();
        game.setName("converted-secret");
        game.setVp(1);
        game.setHasEnded(true);
        game.addToSoToPoList("accept_bribes_pbd100");

        Player winner = game.addPlayer("winner", "Winner");
        winner.setFaction("jolnar");
        winner.setColor("yellow");
        winner.setSecretScored("accept_bribes_pbd100");
        winner.setSecretScored("rule_a_diverse_empire_pbd100");

        int[] playersByScoredAPSecretCount = new int[5];
        int[] winsByScoredAPSecretCount = new int[5];
        int[] playersByScoredSecretCount = new int[5];
        int[] winsByScoredSecretCount = new int[5];
        int[][] playersByExactScoredSecretCountAndMinimumAPCount = new int[5][5];
        int[][] winsByExactScoredSecretCountAndMinimumAPCount = new int[5][5];

        SecretObjectiveWinChanceStatisticsService.collectSecretObjectiveWinChanceStats(
                game,
                playersByScoredAPSecretCount,
                winsByScoredAPSecretCount,
                playersByScoredSecretCount,
                winsByScoredSecretCount,
                playersByExactScoredSecretCountAndMinimumAPCount,
                winsByExactScoredSecretCountAndMinimumAPCount,
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>());

        assertEquals(0, playersByScoredSecretCount[0]);
        assertEquals(0, winsByScoredSecretCount[0]);
        assertEquals(1, playersByScoredSecretCount[1]);
        assertEquals(1, winsByScoredSecretCount[1]);
        assertEquals(0, playersByScoredSecretCount[2]);
        assertEquals(0, winsByScoredSecretCount[2]);
        assertEquals(1, playersByScoredAPSecretCount[0]);
        assertEquals(1, winsByScoredAPSecretCount[0]);
    }

    @Test
    void shouldIgnoreGameForSecretObjectiveStatsOnlyWhenAPlayerHasMoreThanFourRealSecrets() {
        Game validGame = new Game();
        validGame.setName("valid-game");
        Player validPlayer = validGame.addPlayer("valid", "Valid");
        validPlayer.setFaction("obsidian");
        validPlayer.setColor("red");
        validGame.addToSoToPoList("accept_bribes_pbd100");
        validPlayer.setSecretScored("accept_bribes_pbd100");
        validPlayer.setSecret("rule_a_diverse_empire_pbd100");
        validPlayer.setSecret("deep_space_research_pbd100");
        validPlayer.setSecret("monopolize_production_pbd100");
        validPlayer.setSecret("sponsor_data_archives_pbd100");

        Game ignoredGame = new Game();
        ignoredGame.setName("ignored-game");
        Player ignoredPlayer = ignoredGame.addPlayer("ignored", "Ignored");
        ignoredPlayer.setFaction("hacan");
        ignoredPlayer.setColor("blue");
        ignoredPlayer.setSecretScored("accept_bribes_pbd100");
        ignoredPlayer.setSecret("rule_a_diverse_empire_pbd100");
        ignoredPlayer.setSecret("deep_space_research_pbd100");
        ignoredPlayer.setSecret("gather_a_legion_pbd100");
        ignoredPlayer.setSecret("sponsor_data_archives_pbd100");

        assertFalse(SecretObjectiveWinChanceStatisticsService.shouldIgnoreGameForSecretObjectiveStats(validGame));
        assertTrue(SecretObjectiveWinChanceStatisticsService.shouldIgnoreGameForSecretObjectiveStats(ignoredGame));
    }
}
