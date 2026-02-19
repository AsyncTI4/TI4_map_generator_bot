package ti4.service.statistics.game;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class GameModeStatisticsServiceTest {

    @Test
    void formatModeStatisticsIncludesCountsAndPercentages() {
        Map<String, AtomicInteger> modeCounts = new LinkedHashMap<>();
        modeCounts.put("Prophecy of Kings", new AtomicInteger(8));
        modeCounts.put("Thunder's Edge", new AtomicInteger(2));
        modeCounts.put("Discordant Stars", new AtomicInteger(5));

        String message = GameModeStatisticsService.formatModeStatistics(10, modeCounts);

        assertThat(message).contains("Game count by mode (total games: 10):");
        assertThat(message).contains("- Prophecy of Kings: 8 (80.0%)");
        assertThat(message).contains("- Thunder's Edge: 2 (20.0%)");
        assertThat(message).contains("- Discordant Stars: 5 (50.0%)");
    }

    @Test
    void formatModeStatisticsHandlesZeroGames() {
        Map<String, AtomicInteger> modeCounts = new LinkedHashMap<>();
        modeCounts.put("Prophecy of Kings", new AtomicInteger());

        String message = GameModeStatisticsService.formatModeStatistics(0, modeCounts);

        assertThat(message).contains("- Prophecy of Kings: 0 (0.0%)");
    }
}
