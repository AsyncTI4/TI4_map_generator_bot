package ti4.ai.tactical;

import java.util.Map;

record ContextTerms(
        double ownObjectiveGain,
        Map<String, Double> exploreValues,
        double spaceSecretBonus,
        double groundSecretBonus,
        double homeDefence,
        double retakeValue,
        double makeAnExample,
        Map<String, Double> territoryValues,
        double exposedObjective,
        double seatStake) {

    static ContextTerms none() {
        return new ContextTerms(0, Map.of(), 0, 0, 0, 0, 0, Map.of(), 0, 0);
    }
}
