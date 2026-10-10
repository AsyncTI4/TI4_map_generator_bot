package ti4.ai.strategy;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import ti4.ai.eval.BoardView;
import ti4.ai.tactical.ProductionPlanner;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.model.TechnologyModel;
import ti4.model.TechnologyModel.TechnologyType;

@UtilityClass
public class PrerequisiteSkips {

    static final double VALUE_PER_RESOURCE = 0.7;
    private static final String PSYCHOARCHAEOLOGY = "pa";
    private static final String AI_DEVELOPMENT = "aida";
    private static final Map<Character, TechnologyType> COLOURS = Map.of(
            'B', TechnologyType.PROPULSION,
            'G', TechnologyType.BIOTIC,
            'R', TechnologyType.WARFARE,
            'Y', TechnologyType.CYBERNETIC);

    public record Plan(List<String> planets, boolean aiDevelopment, double resourcesLost) {

        static final Plan NONE = new Plan(List.of(), false, 0);

        public double valueLost() {
            return VALUE_PER_RESOURCE * resourcesLost;
        }
    }

    public static Plan of(Game game, Player seat, TechnologyModel tech) {
        Map<TechnologyType, Integer> missing = missing(seat, tech);
        if (missing.isEmpty() || seat.hasTech(PSYCHOARCHAEOLOGY)) return Plan.NONE;
        List<String> planets = new ArrayList<>();
        double lost = 0;
        int shortfall = 0;
        for (Map.Entry<TechnologyType, Integer> colour : missing.entrySet()) {
            List<String> candidates = seat.getReadiedPlanets().stream()
                    .filter(planet -> !planets.contains(planet) && hasSpecialty(game, planet, colour.getKey()))
                    .sorted(Comparator.comparingInt(planet -> spendValue(game, planet)))
                    .limit(colour.getValue())
                    .toList();
            planets.addAll(candidates);
            lost += candidates.stream()
                    .mapToInt(planet -> spendValue(game, planet))
                    .sum();
            shortfall += colour.getValue() - candidates.size();
        }
        boolean aiDevelopment = tech.isUnitUpgrade() && seat.hasTechReady(AI_DEVELOPMENT);
        if (!aiDevelopment) return new Plan(planets, false, lost);
        int aidaCost = ProductionPlanner.aidaDiscount(seat);
        if (shortfall > 0) return new Plan(planets, true, lost + aidaCost);
        String dearest = planets.stream()
                .max(Comparator.comparingInt(planet -> spendValue(game, planet)))
                .orElseThrow();
        if (spendValue(game, dearest) <= aidaCost) return new Plan(planets, false, lost);
        List<String> kept =
                planets.stream().filter(planet -> !planet.equals(dearest)).toList();
        return new Plan(kept, true, lost - spendValue(game, dearest) + aidaCost);
    }

    private static Map<TechnologyType, Integer> missing(Player seat, TechnologyModel tech) {
        String requirements = tech.getRequirements().orElse("");
        Map<TechnologyType, Integer> missing = new EnumMap<>(TechnologyType.class);
        for (Map.Entry<Character, TechnologyType> colour : COLOURS.entrySet()) {
            long needed = requirements
                    .chars()
                    .filter(letter -> letter == colour.getKey())
                    .count();
            int owned = ButtonHelper.getNumberOfCertainTypeOfTech(seat, colour.getValue());
            int gap = (int) Math.max(0, needed - owned);
            if (gap > 0) missing.put(colour.getValue(), gap);
        }
        return missing;
    }

    private static boolean hasSpecialty(Game game, String planet, TechnologyType colour) {
        Planet info = game.getPlanetsInfo().get(planet);
        return info != null
                && info.getTechSpecialities().stream().anyMatch(specialty -> specialty.equalsIgnoreCase(colour.name()));
    }

    private static int spendValue(Game game, String planet) {
        return Math.max(BoardView.planetResources(game, planet), BoardView.planetInfluence(game, planet));
    }
}
