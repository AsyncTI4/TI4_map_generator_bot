package ti4.ai.runtime;

import java.util.Map;
import lombok.experimental.UtilityClass;
import ti4.ai.strategy.SecondaryQueue;
import ti4.ai.tactical.FractureMarch;
import ti4.ai.tactical.SlayIntent;

@UtilityClass
public class AiGameState {

    public record Snapshot(
            Map<String, Long> openFollows, Map<String, String> slayIntents, Map<String, FractureMarch> marches) {}

    public static Snapshot snapshot(String gameName) {
        return new Snapshot(
                SecondaryQueue.snapshot(gameName), SlayIntent.snapshot(gameName), FractureMarch.snapshot(gameName));
    }

    public static void restore(String gameName, Snapshot snapshot) {
        SecondaryQueue.restore(gameName, snapshot.openFollows());
        SlayIntent.restore(gameName, snapshot.slayIntents());
        FractureMarch.restore(gameName, snapshot.marches());
    }

    public static void forget(String gameName) {
        SecondaryQueue.forget(gameName);
        SlayIntent.forget(gameName);
        FractureMarch.forget(gameName);
    }
}
