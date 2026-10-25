package ti4.service.statistics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import ti4.service.statistics.game.WinningPathBreakdown;

class ScoringStatisticsServiceTest {

    private static WinningPathBreakdown path(int stage1s, int stage2s, int secrets, int supports) {
        return new WinningPathBreakdown(stage1s, stage2s, secrets, supports, Map.of());
    }

    private static String report(List<WinningPathBreakdown> winners) {
        return ScoringStatisticsService.buildReport(winners, Optional.empty(), "Thunder's Edge games");
    }

    private static String compareReport(List<WinningPathBreakdown> winners, List<WinningPathBreakdown> baseline) {
        return ScoringStatisticsService.buildReport(
                winners, Optional.of(new ScoringStatisticsService.Sample(baseline)), "Thunder's Edge games");
    }

    private static void addWinners(List<WinningPathBreakdown> winners, WinningPathBreakdown path, int count) {
        for (int i = 0; i < count; i++) {
            winners.add(path);
        }
    }

    @Test
    void reportsNoGamesWhenThereAreNoWinners() {
        String report = report(List.of());

        assertTrue(report.contains("No games matched."));
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

        String report = report(winners);

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

        String report = report(winners);

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

        String report = report(winners);

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

        String report = report(winners);

        assertFalse(report.contains("**Support for the Throne**"));
        assertFalse(report.contains("Supports for the Thrones"));
    }

    @Test
    void includesOtherPointSourcesFoundInTheTopPaths() {
        List<WinningPathBreakdown> winners = new ArrayList<>();
        addWinners(winners, new WinningPathBreakdown(4, 3, 1, 0, Map.of(WinningPathBreakdown.MUTINY, 1)), 4);
        addWinners(winners, new WinningPathBreakdown(4, 3, 2, 0, Map.of()), 4);

        String report = report(winners);

        assertTrue(report.contains("**mutiny**"));
        assertTrue(report.contains("- 1 mutiny: 50%"));
    }

    @Test
    void breaksTiesBetweenEquallyCommonPathsDeterministically() {
        List<WinningPathBreakdown> winners = List.of(path(2, 2, 1, 0), path(3, 3, 1, 0));

        assertEquals(report(winners), report(winners));
    }

    @Test
    void withoutABaselineThereAreNoPercentChanges() {
        List<WinningPathBreakdown> winners = new ArrayList<>();
        addWinners(winners, path(3, 3, 1, 0), 5);

        String report = report(winners);

        assertFalse(report.contains("Parentheses show"));
        assertFalse(report.contains("(+"));
        assertFalse(report.contains("(n/a)"));
        assertFalse(report.contains("(new)"));
    }

    @Test
    void comparisonShowsPercentChangeFromTheBaselineShare() {
        List<WinningPathBreakdown> winners = new ArrayList<>();
        addWinners(winners, path(3, 3, 1, 0), 4);
        addWinners(winners, path(2, 3, 1, 0), 6);
        List<WinningPathBreakdown> baseline = new ArrayList<>();
        addWinners(baseline, path(3, 3, 1, 0), 2);
        addWinners(baseline, path(2, 3, 1, 0), 8);

        String report = compareReport(winners, baseline);

        assertTrue(report.contains("Parentheses show the percent change from the 10 winners"));
        assertTrue(report.contains("- 3 stage 1s: 40% (+100.0%)"));
        assertTrue(report.contains("- 2 stage 1s: 60% (-25.0%)"));
        assertTrue(report.contains("- >= 2 stage 1s: 100% (+0.0%)"));
        assertTrue(report.contains("1. `6 (60%)` (-25.0%) 2 stage 1 objectives"));
        assertTrue(report.contains("2. `4 (40%)` (+100.0%) 3 stage 1 objectives"));
    }

    @Test
    void comparisonMarksSharesTheBaselineNeverHadAsNew() {
        List<WinningPathBreakdown> winners = new ArrayList<>();
        addWinners(winners, path(3, 3, 1, 0), 5);
        List<WinningPathBreakdown> baseline = new ArrayList<>();
        addWinners(baseline, path(2, 3, 1, 0), 5);

        String report = compareReport(winners, baseline);

        assertTrue(report.contains("- 3 stage 1s: 100% (new)"));
    }

    @Test
    void comparisonAgainstNoBaselineWinnersIsNotAvailable() {
        List<WinningPathBreakdown> winners = new ArrayList<>();
        addWinners(winners, path(3, 3, 1, 0), 5);

        String report = compareReport(winners, List.of());

        assertTrue(report.contains("- 3 stage 1s: 100% (n/a)"));
    }
}
