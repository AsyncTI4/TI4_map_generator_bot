package ti4.ai.strategy;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import ti4.ai.eval.BoardView;
import ti4.ai.eval.ValueScale;
import ti4.ai.tactical.ProductionPlanner;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.model.TechnologyModel;
import ti4.model.TechnologyModel.TechnologyType;

@UtilityClass
public class PrerequisiteSkips {

    static final double VALUE_PER_RESOURCE = 0.7 * ValueScale.RESOURCE;
    private static final String PSYCHOARCHAEOLOGY = "pa";
    private static final String AI_DEVELOPMENT = "aida";
    private static final String PROPHETS_TEARS = "prophetstears";

    public record Plan(List<String> planets, boolean aiDevelopment, double resourcesLost, boolean prophetsTears) {

        static final Plan NONE = new Plan(List.of(), false, 0, false);

        Plan(List<String> planets, boolean aiDevelopment, double resourcesLost) {
            this(planets, aiDevelopment, resourcesLost, false);
        }

        public double valueLost() {
            return VALUE_PER_RESOURCE * resourcesLost;
        }
    }

    public static Plan of(Game game, Player seat, TechnologyModel tech) {
        Plan plan = withoutProphetsTears(game, seat, tech);
        if (!seat.hasRelicReady(PROPHETS_TEARS) || missing(seat, tech).isEmpty() || seat.hasTech(PSYCHOARCHAEOLOGY)) {
            return plan;
        }
        return withProphetsTears(game, plan);
    }

    private static Plan withProphetsTears(Game game, Plan plan) {
        Optional<String> dearest =
                plan.planets().stream().max(Comparator.comparingInt(planet -> spendValue(game, planet)));
        if (dearest.isEmpty()) return new Plan(plan.planets(), plan.aiDevelopment(), plan.resourcesLost(), true);
        List<String> kept = plan.planets().stream()
                .filter(planet -> !planet.equals(dearest.get()))
                .toList();
        return new Plan(kept, plan.aiDevelopment(), plan.resourcesLost() - spendValue(game, dearest.get()), true);
    }

    private static Plan withoutProphetsTears(Game game, Player seat, TechnologyModel tech) {
        Map<TechnologyType, Integer> missing = missing(seat, tech);
        if (missing.isEmpty() || seat.hasTech(PSYCHOARCHAEOLOGY)) return Plan.NONE;
        List<String> planets = new ArrayList<>();
        double lost = 0;
        int shortfall = 0;
        for (Map.Entry<TechnologyType, Integer> colour : missing.entrySet()) {
            List<String> candidates = seat.getReadiedPlanets().stream()
                    .filter(planet -> !planets.contains(planet) && hasSpecialty(game, seat, planet, colour.getKey()))
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

    static Map<TechnologyType, Integer> missing(Player seat, TechnologyModel tech) {
        return TechColours.missing(seat, tech.getRequirements().orElse(""), TechColours.owned(seat, null), Map.of());
    }

    private static boolean hasSpecialty(Game game, Player seat, String planet, TechnologyType colour) {
        Planet info = game.getPlanetsInfo().get(planet);
        if (info == null) return false;
        return info.getTechSpecialities().stream()
                .anyMatch(specialty -> specialty.equalsIgnoreCase(colour.name())
                        || (TechColours.pooled(seat, colour) && pooledSpecialty(seat, specialty)));
    }

    private static boolean pooledSpecialty(Player seat, String specialty) {
        return TechColours.pool(seat).stream().anyMatch(colour -> colour.name().equalsIgnoreCase(specialty));
    }

    private static int spendValue(Game game, String planet) {
        return Math.max(BoardView.planetResources(game, planet), BoardView.planetInfluence(game, planet));
    }
}
