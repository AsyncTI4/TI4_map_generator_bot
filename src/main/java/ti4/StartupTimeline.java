package ti4;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.function.LongSupplier;

final class StartupTimeline {

    private final long startedAtMillis;
    private final LongSupplier clock;
    private final List<String> stages = new ArrayList<>();
    private long lastMarkMillis;

    StartupTimeline(long startedAtMillis, LongSupplier clock) {
        this.startedAtMillis = startedAtMillis;
        this.clock = clock;
        lastMarkMillis = startedAtMillis;
    }

    static StartupTimeline sinceJvmStart() {
        return new StartupTimeline(ManagementFactory.getRuntimeMXBean().getStartTime(), System::currentTimeMillis);
    }

    void mark(String stage) {
        long now = clock.getAsLong();
        stages.add(String.format("%,d MS %s", now - lastMarkMillis, stage));
        lastMarkMillis = now;
    }

    String summary() {
        return String.format(
                "STARTUP TOOK %,d MS AFTER THE JVM STARTED: %s",
                lastMarkMillis - startedAtMillis, String.join(", ", stages));
    }
}
