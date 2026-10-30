package ti4.ai.brain;

import java.util.Map;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import ti4.ai.nekro.NekroBrain;

@UtilityClass
public class FactionBrainRegistry {

    private static final FactionBrain RULES = new NekroBrain();
    private static final Map<String, FactionBrain> BRAINS = Map.of("nekro", RULES, "standard", RULES);

    public static Optional<FactionBrain> forBrainId(String brainId) {
        return Optional.ofNullable(BRAINS.get(brainId));
    }
}
