package ti4.ai.strategy;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import ti4.ai.brain.StrategyCard;
import ti4.ai.expedition.ExpeditionUnlock;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.model.TechnologyModel;
import ti4.model.TechnologyModel.TechnologyType;
import ti4.service.tech.ListTechService;

@UtilityClass
public class ReadyingValue {

    private static final String PSYCHOARCHAEOLOGY = "pa";

    public record Pick(String planet, double value, boolean unlocksScore, double techSkipValue) {}

    public static double of(Game game, Player seat, Collection<String> planets) {
        if (planets.isEmpty()) return 0;
        return CardValueScale.READY_SHARE
                * DiplomacyUse.readying(game, seat, planets).use();
    }

    public static Optional<Pick> bestSingle(Game game, Player seat, Collection<String> candidates) {
        return eachAlone(game, seat, candidates).stream().findFirst();
    }

    public static List<Pick> eachAlone(Game game, Player seat, Collection<String> candidates) {
        return DiplomacyUse.eachAlone(game, seat, candidates).stream()
                .map(choice -> pick(game, seat, choice))
                .sorted(Comparator.comparingDouble(Pick::value).reversed().thenComparing(Pick::planet))
                .toList();
    }

    private static Pick pick(Game game, Player seat, DiplomacyUse.Choice choice) {
        String planet = choice.planets().getFirst();
        double spend = choice.unlocksScore()
                ? CardValueScale.SPEND_UNLOCK_SHARE * CardValueScale.VICTORY_POINT
                : CardValueScale.READY_SHARE * choice.use();
        double skip = techSkipValue(game, seat, planet);
        double expedition = ExpeditionUnlock.gainFromReadying(game, seat, choice.planets());
        return new Pick(planet, spend + skip + expedition, choice.unlocksScore(), skip);
    }

    static double techSkipValue(Game game, Player seat, String planet) {
        Planet info = game.getPlanetsInfo().get(planet);
        if (info == null
                || info.getTechSpecialities().isEmpty()
                || seat.hasTech(PSYCHOARCHAEOLOGY)
                || StrategyCardRules.cannotResearch(seat)
                || !researchComing(game, seat)) {
            return 0;
        }
        Map<TechnologyType, Integer> skips = specialties(game, seat.getReadiedPlanets());
        Map<TechnologyType, Integer> withPlanet = specialties(game, seat.getReadiedPlanets());
        specialties(game, List.of(planet)).forEach((type, count) -> withPlanet.merge(type, count, Integer::sum));
        double current = ResearchPolicy.bestResearchable(game, seat)
                .map(alias -> ResearchPolicy.researchValue(game, seat, alias))
                .orElse(0.0);
        double skipCost = PrerequisiteSkips.VALUE_PER_RESOURCE
                * Math.max(Math.max(0, info.getResources()), Math.max(0, info.getInfluence()));
        double best = current;
        for (TechnologyModel tech : unownedTechnologies(game, seat)) {
            Map<TechnologyType, Integer> missing = PrerequisiteSkips.missing(seat, tech);
            if (missing.isEmpty() || covered(missing, skips) || !covered(missing, withPlanet)) continue;
            best = Math.max(best, ResearchPolicy.value(game, seat, tech.getAlias()) - skipCost);
        }
        return ResearchPolicy.TECH_TEMPO * (best - current);
    }

    private static boolean researchComing(Game game, Player seat) {
        for (Player holder : game.getRealPlayers()) {
            for (int initiative : holder.getSCs()) {
                if (StrategyCard.of(game, initiative) != StrategyCard.TECHNOLOGY) continue;
                if (game.getPlayedSCs().contains(initiative)) continue;
                if (holder == seat || seat.getStrategicCC() > 0) return true;
            }
        }
        return false;
    }

    private static List<TechnologyModel> unownedTechnologies(Game game, Player seat) {
        List<TechnologyModel> techs = new ArrayList<>();
        for (TechnologyType type : TechnologyType.mainFive) {
            techs.addAll(ListTechService.getAllTechOfAType(game, type.toString().toLowerCase(), seat, false, false));
        }
        return techs.stream()
                .filter(tech -> !ListTechService.isTechResearchable(tech, seat))
                .toList();
    }

    private static Map<TechnologyType, Integer> specialties(Game game, Collection<String> planets) {
        Map<TechnologyType, Integer> counts = new EnumMap<>(TechnologyType.class);
        for (String name : planets) {
            Planet planet = game.getPlanetsInfo().get(name);
            if (planet == null || !ButtonHelper.checkForTechSkips(game, name)) continue;
            for (String specialty : planet.getTechSpecialities()) {
                for (TechnologyType type : TechnologyType.mainFive) {
                    if (type.toString().equalsIgnoreCase(specialty)) counts.merge(type, 1, Integer::sum);
                }
            }
        }
        return counts;
    }

    private static boolean covered(Map<TechnologyType, Integer> missing, Map<TechnologyType, Integer> skips) {
        return missing.entrySet().stream().allMatch(gap -> skips.getOrDefault(gap.getKey(), 0) >= gap.getValue());
    }
}
