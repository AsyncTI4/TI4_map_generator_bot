package ti4.discord.interactions.buttons;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.junit.jupiter.api.Test;

class ButtonRuntimeMonitorTest {

    // Deep stubs let a slow press build its warning message without a real Discord event.
    private final ButtonInteractionEvent event = mock(ButtonInteractionEvent.class, RETURNS_DEEP_STUBS);

    @Test
    void statisticsBreakEachStageOutWithPercentiles() {
        ButtonRuntimeMonitor monitor = new ButtonRuntimeMonitor();
        for (int i = 0; i < 10; i++) {
            monitor.recordQueued();
            monitor.recordStarted();
            monitor.submit(event, fastPress());
        }

        String statistics = monitor.formatStatistics("now");

        assertThat(statistics)
                .contains("Total button presses: 10")
                .contains("Threshold misses: 0% (0)")
                .contains("0 from a non-player or non-owner, 0 not run");
        assertThat(rowFor(statistics, "preprocessing")).contains("10", "85.0ms", "85ms");
        assertThat(rowFor(statistics, "gateway")).contains("80.0ms", "80ms");
        assertThat(rowFor(statistics, "handoff")).contains("5.0ms");
        assertThat(rowFor(statistics, "processing")).contains("76.0ms");
        assertThat(rowFor(statistics, "resolve")).contains("34.0ms");
        assertThat(rowFor(statistics, "replay-snap")).contains(" 0 ", "-");
        assertThat(rowFor(statistics, "replay-settle")).contains(" 0 ", "-");
    }

    @Test
    void statisticsCountPressesThatNeverRanOrWereInvalid() {
        ButtonRuntimeMonitor monitor = new ButtonRuntimeMonitor();
        // 4 presses queued: 1 rejected because the game was busy, 1 from a non-player, 2 processed.
        for (int i = 0; i < 4; i++) monitor.recordQueued();
        for (int i = 0; i < 3; i++) monitor.recordStarted();
        monitor.recordInvalid();
        monitor.submit(event, fastPress());
        monitor.submit(event, fastPress());

        assertThat(monitor.formatStatistics("now"))
                .contains("Total button presses: 2")
                .contains("1 from a non-player or non-owner, 1 not run (game busy or bot paused)");
    }

    @Test
    void thresholdMissesAreReportedAsAPercentage() {
        ButtonRuntimeMonitor monitor = new ButtonRuntimeMonitor();
        for (int i = 0; i < 199; i++) {
            monitor.submit(event, fastPress());
        }
        ButtonPressTimeline slowPress = ButtonPressTimeline.received(0, 0);
        slowPress.markStarted(3000);
        slowPress.markFinished(3010);
        when(event.getButton().getLabel()).thenReturn("End Turn");

        monitor.submit(event, slowPress);

        assertThat(monitor.formatStatistics("now")).contains("Threshold misses: 0.5% (1)");
    }

    private static ButtonPressTimeline fastPress() {
        ButtonPressTimeline timeline = ButtonPressTimeline.received(1000, 1080);
        timeline.markStarted(1085);
        timeline.markCompleted(ButtonPressStage.CONTEXT, 1105);
        timeline.markCompleted(ButtonPressStage.LOG, 1106);
        timeline.markCompleted(ButtonPressStage.RESOLVE, 1140);
        timeline.markCompleted(ButtonPressStage.SAVE, 1160);
        timeline.markFinished(1161);
        return timeline;
    }

    private static String rowFor(String statistics, String label) {
        return statistics
                .lines()
                .filter(line -> line.trim().startsWith(label + " "))
                .findFirst()
                .orElseThrow();
    }
}
