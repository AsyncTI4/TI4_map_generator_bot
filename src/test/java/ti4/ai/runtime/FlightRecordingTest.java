package ti4.ai.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FlightRecordingTest {

    @Test
    void writesTheRecordingWhereTheBatchAskedForIt(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("nested").resolve("batch.jfr");
        System.setProperty("ai.selfplay.jfr", file.toString());
        try (FlightRecording ignored = FlightRecording.startIfAsked()) {
            // Something for the recorder to sample.
            long sum = 0;
            for (int i = 0; i < 1_000_000; i++) sum += i;
            assertThat(sum).isPositive();
        } finally {
            System.clearProperty("ai.selfplay.jfr");
        }

        assertThat(file).exists();
        assertThat(Files.size(file)).isPositive();
    }

    @Test
    void doesNothingWithoutTheProperty() throws Exception {
        System.clearProperty("ai.selfplay.jfr");
        try (FlightRecording recording = FlightRecording.startIfAsked()) {
            assertThat(recording).isNotNull();
        }
    }
}
