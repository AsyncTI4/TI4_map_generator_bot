package ti4.ai.runtime;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import jdk.jfr.Configuration;
import jdk.jfr.Recording;

// -Dai.selfplay.jfr=<file.jfr> records the batch with Java Flight Recorder's "profile" settings and writes the
// recording there when the batch ends (or when the JVM exits, if the batch never gets that far). Surefire's argLine is
// fixed in the pom, so the recorder is started from the test instead of with -XX:StartFlightRecording. Stacks stop at
// JFR's default depth of 64 frames, keeping the frames nearest the leaf. Every load and save of a game file is also
// traced with its caller (jdk.MethodTrace / jdk.MethodTiming), since the harness and the engine both reload the save
// from disk. Open it with `jfr print`, `jfr view` or JDK Mission Control.
final class FlightRecording implements AutoCloseable {

    private static final String GAME_FILE_METHODS =
            "ti4.game.persistence.GameLoadService::load;ti4.game.persistence.GameSaveService::save";
    private static final long MAX_BYTES = 6L * 1024 * 1024 * 1024;

    private final Recording recording;
    private final Path destination;

    private FlightRecording(Recording recording, Path destination) {
        this.recording = recording;
        this.destination = destination;
    }

    static FlightRecording startIfAsked() throws Exception {
        String file = System.getProperty("ai.selfplay.jfr", "");
        if (file.isBlank()) return new FlightRecording(null, null);
        Path destination = Path.of(file).toAbsolutePath();
        if (destination.getParent() != null) Files.createDirectories(destination.getParent());
        Recording recording = new Recording(Configuration.getConfiguration("profile"));
        recording.setName("ai-selfplay");
        recording.setToDisk(true);
        recording.setMaxSize(MAX_BYTES);
        recording.setDestination(destination);
        recording.setDumpOnExit(true);
        recording
                .enable("jdk.MethodTrace")
                .with("filter", GAME_FILE_METHODS)
                .withStackTrace()
                .withThreshold(Duration.ofMillis(1));
        recording.enable("jdk.MethodTiming").with("filter", GAME_FILE_METHODS).withPeriod(Duration.ofMinutes(10));
        recording.start();
        System.out.println("Flight recording started, it will be written to " + destination);
        return new FlightRecording(recording, destination);
    }

    // A failure to write the recording is reported, not thrown, so it never costs the batch its report.
    @Override
    public void close() {
        if (recording == null) return;
        try {
            recording.stop();
            System.out.println("Flight recording written to " + destination);
        } catch (RuntimeException e) {
            System.out.println("Flight recording could not be written to " + destination + ": " + e);
        } finally {
            recording.close();
        }
    }
}
