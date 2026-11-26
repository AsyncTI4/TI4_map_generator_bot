package ti4.ai.eval;

import lombok.experimental.UtilityClass;

@UtilityClass
public class MovementAssumptions {

    public static String key() {
        return MoveBoost.boostedFaction() + "/" + FleetLimit.unlimitedFaction() + "/" + MovementGraph.assumesAntimass()
                + "/" + MoveEnablers.assumedKey();
    }
}
