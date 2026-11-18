package ti4.ai.brain;

import java.util.Map;
import java.util.Optional;
import lombok.experimental.UtilityClass;

@UtilityClass
public class FactionBrainRegistry {

    private static final String SAVED_NEKRO_ID = "nekro";
    private static final FactionBrain RULES = new StandardBrain();
    private static final Map<String, FactionBrain> BRAINS = Map.of(StandardBrain.ID, RULES, SAVED_NEKRO_ID, RULES);

    public static Optional<FactionBrain> forBrainId(String brainId) {
        return Optional.ofNullable(BRAINS.get(brainId));
    }
}
