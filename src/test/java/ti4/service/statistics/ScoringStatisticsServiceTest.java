package ti4.service.statistics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import ti4.service.statistics.game.WinningPathBreakdown;

class ScoringStatisticsServiceTest {

    private static WinningPathBreakdown path(int stage1s, int stage2s, int secrets, int supports) {
        return new WinningPathBreakdown(stage1s, stage2s, secrets, supports, Map.of());
    }

    private static void addWinners(List<WinningPathBreakdown> winners, WinningPathBreakdown path, int count) {
        for (int i = 0; i < count; i++) {
            winners.add(path);
        }
    }

    @Test
    void reportsNoGamesWhenThereAreNoWinners() {
        String report = ScoringStatisticsService.buildReport(List.of());

        assertTrue(report.contains("No games match the selected filters."));
    }

    @Test
    void listsOnlyTheFiveMostCommonPathsMostCommonFirst() {
        List<WinningPathBreakdown> winners = new ArrayList<>();
        addWinners(winners, path(3, 3, 1, 0), 10);
        addWinners(winners, path(4, 2, 1, 0), 8);
        addWinners(winners, path(5, 2, 0, 0), 6);
        addWinners(winners, path(2, 4, 2, 0), 4);
        addWinners(winners, path(1, 1, 3, 1), 3);
        addWinners(winners, path(0, 0, 0, 0), 1);

        String report = ScoringStatisticsService.buildReport(winners);

        assertTrue(report.contains("1. `10 (31.25%)` 3 stage 1 objectives, 3 stage 2 objectives, 1 secret objectives"));
        assertTrue(report.contains("5. `3 (9.38%)` 1 stage 1 objectives"));
        assertFalse(report.contains("6. `"));
    }

    @Test
    void sharesAreOutOfAllWinnersAndCoverEveryCountOfATypeInTheTopPaths() {
        List<WinningPathBreakdown> winners = new ArrayList<>();
        addWinners(winners, path(3, 3, 1, 0), 5);
        addWinners(winners, path(2, 3, 1, 1), 3);
        addWinners(winners, path(1, 3, 0, 0), 2);

        String report = ScoringStatisticsService.buildReport(winners);

        assertTrue(report.contains("- 1 stage 1: 20%"));
        assertTrue(report.contains("- 2 stage 1s: 30%"));
        assertTrue(report.contains("- 3 stage 1s: 50%"));
        assertTrue(report.contains("- 3 stage 2s: 100%"));
        assertTrue(report.contains("- 1 secret objective: 80%"));
        assertTrue(report.contains("- 1 _Support for the Throne_: 30%"));
    }

    @Test
    void addsCumulativeAtLeastRowsUpToTheMostAnyWinnerScored() {
        List<WinningPathBreakdown> winners = new ArrayList<>();
        addWinners(winners, path(3, 3, 1, 0), 5);
        addWinners(winners, path(2, 3, 1, 1), 3);
        addWinners(winners, path(1, 3, 0, 0), 2);

        String report = ScoringStatisticsService.buildReport(winners);

        assertTrue(report.contains("- >= 1 stage 1s: 100%"));
        assertTrue(report.contains("- >= 2 stage 1s: 80%"));
        assertTrue(report.contains("- >= 3 stage 1s: 50%"));
        assertFalse(report.contains(">= 4 stage 1s"));
        assertTrue(report.contains("- >= 1 secret objectives: 80%"));
        assertTrue(report.contains("- >= 1 _Supports for the Thrones_: 30%"));
    }

    @Test
    void omitsTypesThatNoTopPathScoredFrom() {
        List<WinningPathBreakdown> winners = new ArrayList<>();
        addWinners(winners, path(5, 5, 0, 0), 6);
        addWinners(winners, path(4, 4, 2, 0), 5);
        addWinners(winners, path(4, 4, 1, 0), 5);
        addWinners(winners, path(3, 4, 1, 0), 4);
        addWinners(winners, path(3, 3, 1, 0), 3);
        addWinners(winners, path(0, 0, 0, 2), 1);

        String report = ScoringStatisticsService.buildReport(winners);

        assertFalse(report.contains("**Support for the Throne**"));
        assertFalse(report.contains("Supports for the Thrones"));
    }

    @Test
    void includesOtherPointSourcesFoundInTheTopPaths() {
        List<WinningPathBreakdown> winners = new ArrayList<>();
        addWinners(winners, new WinningPathBreakdown(4, 3, 1, 0, Map.of(WinningPathBreakdown.MUTINY, 1)), 4);
        addWinners(winners, new WinningPathBreakdown(4, 3, 2, 0, Map.of()), 4);

        String report = ScoringStatisticsService.buildReport(winners);

        assertTrue(report.contains("**mutiny**"));
        assertTrue(report.contains("- 1 mutiny: 50%"));
    }

    @Test
    void breaksTiesBetweenEquallyCommonPathsDeterministically() {
        List<WinningPathBreakdown> winners = List.of(path(2, 2, 1, 0), path(3, 3, 1, 0));

        assertEquals(ScoringStatisticsService.buildReport(winners), ScoringStatisticsService.buildReport(winners));
    }
}
