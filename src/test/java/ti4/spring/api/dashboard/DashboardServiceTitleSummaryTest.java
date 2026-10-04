package ti4.spring.api.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import ti4.spring.service.persistence.EarnedTitle;
import ti4.spring.service.title.PlayerTitleService;

class DashboardServiceTitleSummaryTest {

    @Test
    void titlesAreCountedPerGameNewestGameFirstAndSkipPlaceholders() {
        PlayerTitleService playerTitleService = mock(PlayerTitleService.class);
        when(playerTitleService.getEndedGameTitles("1"))
                .thenReturn(List.of(
                        new EarnedTitle("Rules Master", "pbd9"),
                        new EarnedTitle("Rules Master", "pbd100"),
                        new EarnedTitle("Hard To Kill", "pbd9"),
                        new EarnedTitle("**", "pbd9")));
        DashboardService service = new DashboardService(mock(PlayerAggregatesService.class), playerTitleService);

        PlayerDashboardResponse.TitleSummary summary = service.getTitleSummary("1");

        assertThat(summary.totalCount()).isEqualTo(3);
        // pbd100 sorts after pbd9 once padded, so it is listed first (newest first).
        assertThat(summary.items())
                .containsExactly(
                        new PlayerDashboardResponse.TitleItem("Rules Master", 2, List.of("pbd100", "pbd9")),
                        new PlayerDashboardResponse.TitleItem("Hard To Kill", 1, List.of("pbd9")));
    }
}
