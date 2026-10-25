package ti4.image.tile;

import java.util.HashSet;
import java.util.Set;
import ti4.logging.BotLogger;
import ti4.logging.LogOrigin;

final class RenderWarnings {

    private final Set<String> reportedKinds = new HashSet<>();

    void warnOnce(String kind, LogOrigin origin, String message) {
        if (reportedKinds.add(kind)) {
            BotLogger.warning(origin, message);
        }
    }
}
