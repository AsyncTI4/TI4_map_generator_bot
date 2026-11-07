package ti4;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import org.junit.jupiter.api.Test;

class StartupTimelineTest {

    @Test
    void eachStageMeasuresTheGapSinceThePreviousMarkAndTheTotalStartsAtTheJvm() {
        Deque<Long> clock = new ArrayDeque<>(List.of(1_400L, 13_400L, 61_400L));
        StartupTimeline startup = new StartupTimeline(1_000L, clock::removeFirst);

        startup.mark("STARTING THE JVM");
        startup.mark("STARTING SPRING");
        startup.mark("CONNECTING TO DISCORD");

        assertThat(startup.summary())
                .isEqualTo("STARTUP TOOK 60,400 MS AFTER THE JVM STARTED: 400 MS STARTING THE JVM,"
                        + " 12,000 MS STARTING SPRING, 48,000 MS CONNECTING TO DISCORD");
    }
}
