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

class EndingRoundPhaseStatisticsServiceTest extends BaseTi4Test {

    @Test
    void collectEndingRoundStatsBucketsOverallAndPerFactionCounts() {
        Game game = new Game();
        game.setName("ending-round-faction-stats");
        game.setHasEnded(true);
        game.setRound(5);
        game.setPhaseOfGame("statusScoring");

        Player winner = game.addPlayer("winner", "Winner");
        winner.setFaction("arborec");
        winner.setColor("green");

        Map<String, Integer> endingRoundAndPhaseCount = new HashMap<>();
        Map<String, EndingRoundPhaseStatisticsService.FactionWinningRoundStats> statsByFaction = new HashMap<>();

        EndingRoundPhaseStatisticsService.collectEndingRoundStats(game, endingRoundAndPhaseCount, statsByFaction);

        assertEquals(1, endingRoundAndPhaseCount.size());
        assertEquals(1, endingRoundAndPhaseCount.get("Round 5 - status"));
        assertEquals(1, statsByFaction.size());
        assertEquals(5.0, statsByFaction.get("arborec").getAverageRound());
        assertEquals("R5SP: 1", statsByFaction.get("arborec").formatRoundPhaseCounts());
    }

    @Test
    void buildEndingRoundPhaseReportFormatsOriginalOutput() {
        String report = EndingRoundPhaseStatisticsService.buildEndingRoundPhaseReport(Map.of(
                "Round 5 - action", 2,
                "Round 4 - status", 1));

        assertTrue(report.contains("1. `2 (67%)` Round 5 - action"));
        assertTrue(report.contains("2. `1 (33%)` Round 4 - status"));
    }

    @Test
    void buildFactionWinningRoundReportFormatsAverageAndOmitsMissingRoundPhaseCounts() {
        EndingRoundPhaseStatisticsService.FactionWinningRoundStats arborecStats =
                new EndingRoundPhaseStatisticsService.FactionWinningRoundStats();
        arborecStats.addWin(4, "AP");
        arborecStats.addWin(4, "AP");
        arborecStats.addWin(4, "SP");
        arborecStats.addWin(5, "AP");
        arborecStats.addWin(5, "AP");

        EndingRoundPhaseStatisticsService.FactionWinningRoundStats argentStats =
                new EndingRoundPhaseStatisticsService.FactionWinningRoundStats();
        argentStats.addWin(5, "AgP");
        argentStats.addWin(6, "SP");

        String report = EndingRoundPhaseStatisticsService.buildFactionWinningRoundReport(Map.of(
                "argent", argentStats,
                "arborec", arborecStats));

        assertTrue(report.contains("Arborec"));
        assertTrue(report.contains("Argent"));
        assertTrue(report.contains("4.4 avg (R4AP: 2, R4SP: 1, R5AP: 2)"));
        assertTrue(report.contains("5.5 avg (R5AgP: 1, R6SP: 1)"));
        assertFalse(report.contains("R4AgP"));
        int arborecIndex = report.indexOf("Arborec");
        int argentIndex = report.indexOf("Argent");
        assertTrue(arborecIndex >= 0);
        assertTrue(argentIndex >= 0);
        assertTrue(arborecIndex < argentIndex);
    }

    @Test
    void formatFullRoundPhaseLabelExpandsPhaseCodes() {
        assertEquals("Round 4 - action", EndingRoundPhaseStatisticsService.formatFullRoundPhaseLabel(4, "AP"));
        assertEquals("Round 5 - status", EndingRoundPhaseStatisticsService.formatFullRoundPhaseLabel(5, "SP"));
        assertEquals("Round 6 - agenda", EndingRoundPhaseStatisticsService.formatFullRoundPhaseLabel(6, "AgP"));
        assertEquals("Round 7 - unknown", EndingRoundPhaseStatisticsService.formatFullRoundPhaseLabel(7, "UP"));
    }
}
