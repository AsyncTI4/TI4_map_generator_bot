package ti4.ai.strategy;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import javax.annotation.Nullable;
import lombok.experimental.UtilityClass;
import ti4.ai.eval.GameHorizon;
import ti4.ai.secrets.SecretValue;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.model.Mapper;
import ti4.model.TechnologyModel;
import ti4.model.TechnologyModel.TechnologyType;
import ti4.service.info.ListPlayerInfoService;

@UtilityClass
public class TechPath {

    public static final String ADAPT_NEW_STRATEGIES = "ans";
    public static final String LAWS_OF_PHYSICS = "mlp";
    static final double RESEARCHES_PER_ROUND = 1.0;
    static final double STEP_SHARE = 2.0;
    static final Set<String> VALEFAR_ASSIMILATORS = Set.of("vax", "vay");
    private static final int ADAPT_THRESHOLD = 2;

    public record Route(int researches, List<TechnologyModel> techs, List<String> notes, List<String> scarTechs) {

        public Route(int researches, List<TechnologyModel> techs, List<String> notes) {
            this(researches, techs, notes, List.of());
        }

        public boolean needsScarPurchase() {
            return !scarTechs.isEmpty();
        }
    }

    public static Optional<Route> route(Game game, Player seat, String objective) {
        return TechRoutes.route(game, seat, objective);
    }

    public static Optional<Route> routeTo(Game game, Player seat, TechnologyModel tech) {
        return TechRoutes.routeTo(game, seat, tech);
    }

    public static boolean follows(String objective) {
        return ADAPT_NEW_STRATEGIES.equals(objective) || LAWS_OF_PHYSICS.equals(objective);
    }

    public static int researchesLeft(Game game) {
        return Math.max(1, (int) Math.floor(GameHorizon.roundsLeft(game) * RESEARCHES_PER_ROUND));
    }

    public static boolean reachable(Game game, Player seat, String objective) {
        OptionalInt needed = researchesNeeded(game, seat, objective, null);
        return needed.isPresent() && needed.getAsInt() <= researchesLeft(game);
    }

    static double gain(Game game, Player seat, String objective, TechnologyModel tech) {
        OptionalInt before = researchesNeeded(game, seat, objective, null);
        if (before.isEmpty() || before.getAsInt() == 0 || before.getAsInt() > researchesLeft(game)) return 0;
        int after = researchesNeeded(game, seat, objective, tech).orElse(before.getAsInt());
        int stepsDone = before.getAsInt() - after;
        if (stepsDone <= 0) return 0;
        return after == 0 ? 1 : Math.min(1, STEP_SHARE * stepsDone / before.getAsInt());
    }

    public static OptionalInt researchesNeeded(Game game, Player seat, String objective) {
        return researchesNeeded(game, seat, objective, null);
    }

    static OptionalInt researchesNeeded(Game game, Player seat, String objective, @Nullable TechnologyModel adding) {
        if (StrategyCardRules.cannotResearch(seat) || game.isTwilightsFallMode()) return OptionalInt.empty();
        int threshold = ListPlayerInfoService.getObjectiveThreshold(objective, game);
        if (threshold <= 0) return OptionalInt.empty();
        if (LAWS_OF_PHYSICS.equals(objective)) {
            int progress = TechColours.lawsProgress(seat, TechColours.owned(seat, adding));
            return OptionalInt.of(Math.max(0, threshold - progress));
        }
        if (ADAPT_NEW_STRATEGIES.equals(objective)) return factionTechnologies(game, seat, threshold, adding);
        return OptionalInt.empty();
    }

    public static OptionalInt factionTechsNeeded(Player seat) {
        int needed = ADAPT_THRESHOLD - SecretValue.ownFactionTechnologies(seat);
        long available = seat.getNotResearchedFactionTechs().stream()
                .filter(alias -> !VALEFAR_ASSIMILATORS.contains(alias) && Mapper.getTech(alias) != null)
                .count();
        return needed <= 0 || available >= needed ? OptionalInt.of(Math.max(0, needed)) : OptionalInt.empty();
    }

    private static OptionalInt factionTechnologies(
            Game game, Player seat, int threshold, @Nullable TechnologyModel adding) {
        boolean addsOwn = adding != null && ListPlayerInfoService.countsForAdaptNewStrategies(adding.getAlias(), false);
        int needed = threshold - SecretValue.ownFactionTechnologies(seat) - (addsOwn ? 1 : 0);
        if (needed <= 0) return OptionalInt.of(0);
        List<TechnologyModel> candidates = new ArrayList<>();
        for (String alias : seat.getNotResearchedFactionTechs()) {
            TechnologyModel tech = Mapper.getTech(alias);
            if (tech == null
                    || VALEFAR_ASSIMILATORS.contains(alias)
                    || (adding != null && adding.getAlias().equals(alias))) {
                continue;
            }
            candidates.add(tech);
        }
        if (candidates.size() < needed) return OptionalInt.empty();
        int cheapest =
                cheapest(seat, candidates, needed, List.of(), TechColours.owned(seat, adding), skips(game, seat));
        return OptionalInt.of(cheapest);
    }

    private static int cheapest(
            Player seat,
            List<TechnologyModel> candidates,
            int needed,
            List<TechnologyModel> chosen,
            Map<TechnologyType, Integer> owned,
            Map<TechnologyType, Integer> skips) {
        if (chosen.size() == needed) return researches(seat, chosen, owned, skips);
        int best = Integer.MAX_VALUE;
        for (TechnologyModel candidate : candidates) {
            if (chosen.contains(candidate)) continue;
            List<TechnologyModel> next = new ArrayList<>(chosen);
            next.add(candidate);
            best = Math.min(best, cheapest(seat, candidates, needed, next, owned, skips));
        }
        return best;
    }

    static int researches(
            Player seat,
            List<TechnologyModel> order,
            Map<TechnologyType, Integer> owned,
            Map<TechnologyType, Integer> skips) {
        Map<TechnologyType, Integer> counts = new EnumMap<>(owned);
        int researches = 0;
        for (TechnologyModel target : order) {
            Map<TechnologyType, Integer> missing =
                    TechColours.missing(seat, target.getRequirements().orElse(""), counts, skips);
            for (Map.Entry<TechnologyType, Integer> gap : missing.entrySet()) {
                counts.merge(gap.getKey(), gap.getValue(), Integer::sum);
                researches += gap.getValue();
            }
            researches++;
            for (TechnologyType colour : target.getTypes()) {
                if (TechnologyType.mainFour.contains(colour)) counts.merge(colour, 1, Integer::sum);
            }
        }
        return researches;
    }

    static Map<TechnologyType, Integer> skips(Game game, Player seat) {
        Map<TechnologyType, Integer> skips = new EnumMap<>(TechnologyType.class);
        for (String name : seat.getPlanets()) {
            Planet planet = game.getPlanetsInfo().get(name);
            if (planet == null) continue;
            for (TechnologyType colour : TechnologyType.mainFour) {
                boolean matches = planet.getTechSpecialities().stream()
                        .anyMatch(specialty -> specialty.equalsIgnoreCase(colour.name()));
                if (matches) skips.merge(colour, 1, Integer::sum);
            }
        }
        return skips;
    }
}
