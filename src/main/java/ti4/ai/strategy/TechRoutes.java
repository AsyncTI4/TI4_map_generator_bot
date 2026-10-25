package ti4.ai.strategy;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.experimental.UtilityClass;
import ti4.ai.secrets.SecretValue;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.model.Mapper;
import ti4.model.TechnologyModel;
import ti4.model.TechnologyModel.TechnologyType;
import ti4.service.info.ListPlayerInfoService;

@UtilityClass
class TechRoutes {

    private static final int MAX_DEPTH = 4;
    private static final int MAX_FILLS = 12;

    static Optional<TechPath.Route> route(Game game, Player seat, String objective) {
        if (!canPlan(game, seat)) return Optional.empty();
        int threshold = ListPlayerInfoService.getObjectiveThreshold(objective, game);
        if (threshold <= 0) return Optional.empty();
        State state = new State(game, seat);
        if (TechPath.ADAPT_NEW_STRATEGIES.equals(objective)) return factionTechs(state, threshold);
        if (ResearchPolicy.UNIT_UPGRADE_OBJECTIVES.contains(objective)) return unitUpgrades(state, threshold);
        if (TechPath.LAWS_OF_PHYSICS.equals(objective)) return lawsOfPhysics(state, threshold);
        if (ResearchPolicy.COLOUR_PAIR_OBJECTIVES.contains(objective)) return colourPairs(state, threshold);
        return Optional.empty();
    }

    static Optional<TechPath.Route> routeTo(Game game, Player seat, TechnologyModel tech) {
        if (!canPlan(game, seat) || seat.hasTech(tech.getAlias())) return Optional.empty();
        State state = new State(game, seat);
        state.add(tech, 0);
        return Optional.of(state.finish());
    }

    private static boolean canPlan(Game game, Player seat) {
        return !StrategyCardRules.cannotResearch(seat) && !game.isTwilightsFallMode();
    }

    private static Optional<TechPath.Route> factionTechs(State state, int threshold) {
        int needed = threshold - SecretValue.ownFactionTechnologies(state.seat);
        if (needed <= 0) return Optional.of(state.finish());
        List<TechnologyModel> candidates = new ArrayList<>();
        for (String alias : state.seat.getNotResearchedFactionTechs()) {
            TechnologyModel tech = Mapper.getTech(alias);
            if (tech != null && !TechPath.VALEFAR_ASSIMILATORS.contains(alias)) candidates.add(tech);
        }
        candidates.sort(Comparator.comparing(TechnologyModel::getAlias));
        if (candidates.size() < needed) return Optional.empty();
        int free = ScarToken.canBuy(state.game, state.seat) ? 1 : 0;
        return Optional.of(cheapest(state, candidates, needed, free));
    }

    private static Optional<TechPath.Route> unitUpgrades(State state, int threshold) {
        int needed = threshold - ButtonHelper.getNumberOfUnitUpgrades(state.seat);
        if (needed <= 0) return Optional.of(state.finish());
        List<TechnologyModel> candidates =
                state.available.stream().filter(TechnologyModel::isUnitUpgrade).toList();
        if (candidates.size() < needed) return Optional.empty();
        return Optional.of(cheapest(state, candidates, needed, 0));
    }

    private static TechPath.Route cheapest(State state, List<TechnologyModel> candidates, int needed, int free) {
        boolean ordered = free > 0 || candidates.stream().anyMatch(State::givesColour);
        Best best = new Best();
        search(state, candidates, needed, free, ordered, 0, new ArrayList<>(), best);
        int paid = best.order.size() - free;
        for (int i = 0; i < best.order.size(); i++) {
            TechnologyModel tech = best.order.get(i);
            if (i < paid) {
                state.add(tech, 0);
            } else {
                state.notes.add("Entropic Scar supplies " + tech.getAlias());
                state.scarTechs.add(tech.getAlias());
            }
        }
        return state.finish();
    }

    private static void search(
            State state,
            List<TechnologyModel> candidates,
            int needed,
            int free,
            boolean ordered,
            int from,
            List<TechnologyModel> chosen,
            Best best) {
        if (chosen.size() == needed) {
            best.offer(state, chosen, free);
            return;
        }
        for (int i = ordered ? 0 : from; i < candidates.size(); i++) {
            TechnologyModel candidate = candidates.get(i);
            if (chosen.contains(candidate)) continue;
            chosen.add(candidate);
            search(state, candidates, needed, free, ordered, i + 1, chosen, best);
            chosen.removeLast();
        }
    }

    private static Optional<TechPath.Route> lawsOfPhysics(State state, int threshold) {
        List<TechnologyType> eligible = lawsColours(state);
        int guard = MAX_FILLS;
        while (TechColours.lawsProgress(state.seat, state.counts) < threshold && guard-- > 0) {
            if (!state.fill(eligible)) return Optional.empty();
        }
        return Optional.of(state.finish());
    }

    private static List<TechnologyType> lawsColours(State state) {
        List<TechnologyType> pool = TechColours.pool(state.seat);
        TechnologyType best = null;
        int pooledTotal = 0;
        for (TechnologyType colour : TechnologyType.mainFour) {
            int count = state.counts.getOrDefault(colour, 0);
            if (pool.contains(colour)) {
                pooledTotal += count;
            } else if (best == null || count > state.counts.getOrDefault(best, 0)) {
                best = colour;
            }
        }
        boolean pooledLeads = !pool.isEmpty() && (best == null || pooledTotal >= state.counts.getOrDefault(best, 0));
        return pooledLeads ? pool : List.of(best);
    }

    private static Optional<TechPath.Route> colourPairs(State state, int threshold) {
        List<TechnologyType> chosen = TechnologyType.mainFour.stream()
                .sorted(Comparator.comparingInt((TechnologyType colour) -> -state.counts.getOrDefault(colour, 0))
                        .thenComparing(Comparator.naturalOrder()))
                .limit(threshold)
                .toList();
        List<TechnologyType> order = chosen.stream()
                .sorted(Comparator.comparingInt(
                        (TechnologyType colour) -> ResearchPolicy.PAIR - state.counts.getOrDefault(colour, 0)))
                .toList();
        for (TechnologyType colour : order) {
            int guard = MAX_FILLS;
            while (state.counts.getOrDefault(colour, 0) < ResearchPolicy.PAIR && guard-- > 0) {
                if (!state.fill(List.of(colour))) return Optional.empty();
            }
        }
        return Optional.of(state.finish());
    }

    private static final class Best {
        private int cost = Integer.MAX_VALUE;
        private double score = Double.NEGATIVE_INFINITY;
        private List<TechnologyModel> order = List.of();

        void offer(State state, List<TechnologyModel> chosen, int free) {
            List<TechnologyModel> paid = chosen.subList(0, chosen.size() - free);
            int candidateCost = TechPath.researches(state.seat, paid, state.counts, state.skips);
            double candidateScore = chosen.stream().mapToDouble(state::score).sum();
            if (candidateCost < cost || candidateCost == cost && candidateScore > score) {
                cost = candidateCost;
                score = candidateScore;
                order = new ArrayList<>(chosen);
            }
        }
    }

    private static final class State {
        private final Game game;
        private final Player seat;
        private final Map<TechnologyType, Integer> counts;
        private final Map<TechnologyType, Integer> skips;
        private final List<TechnologyModel> available;
        private final List<TechnologyModel> techs = new ArrayList<>();
        private final Set<String> taken = new HashSet<>();
        private final List<String> notes = new ArrayList<>();
        private final List<String> scarTechs = new ArrayList<>();
        private int researches;

        State(Game game, Player seat) {
            this.game = game;
            this.seat = seat;
            this.counts = new EnumMap<>(TechColours.owned(seat, null));
            this.skips = TechPath.skips(game, seat);
            this.available = availableTechs(game, seat);
        }

        private static List<TechnologyModel> availableTechs(Game game, Player seat) {
            Set<String> ownFactionTechs = new HashSet<>(seat.getNotResearchedFactionTechs());
            return game.getTechnologyDeck().stream()
                    .distinct()
                    .map(Mapper::getTech)
                    .filter(tech -> tech != null
                            && !seat.hasTech(tech.getAlias())
                            && !seat.getPurgedTechs().contains(tech.getAlias())
                            && (tech.getFaction()
                                            .filter(name -> !name.isBlank())
                                            .isEmpty()
                                    || ownFactionTechs.contains(tech.getAlias())))
                    .sorted(Comparator.comparing(TechnologyModel::getAlias))
                    .toList();
        }

        static boolean givesColour(TechnologyModel tech) {
            return tech.getTypes().stream().anyMatch(TechnologyType.mainFour::contains);
        }

        double score(TechnologyModel tech) {
            return ResearchPolicy.baseValue(tech) + ResearchPrior.nudge(seat.getFaction(), tech.getAlias());
        }

        boolean fill(Collection<TechnologyType> eligible) {
            Optional<TechnologyModel> filler = filler(eligible);
            if (filler.isEmpty()) return false;
            add(filler.get(), 1);
            return true;
        }

        void add(TechnologyModel target, int depth) {
            if (!taken.add(target.getAlias())) return;
            Map<TechnologyType, Integer> missing =
                    TechColours.missing(seat, target.getRequirements().orElse(""), counts, skips);
            for (Map.Entry<TechnologyType, Integer> gap : missing.entrySet()) {
                Collection<TechnologyType> eligible =
                        TechColours.pooled(seat, gap.getKey()) ? TechColours.pool(seat) : List.of(gap.getKey());
                for (int i = 0; i < gap.getValue(); i++) {
                    Optional<TechnologyModel> filler = depth < MAX_DEPTH ? filler(eligible) : Optional.empty();
                    if (filler.isPresent()) {
                        add(filler.get(), depth + 1);
                    } else {
                        counts.merge(gap.getKey(), 1, Integer::sum);
                        researches++;
                    }
                }
            }
            researches++;
            techs.add(target);
            for (TechnologyType colour : target.getTypes()) {
                if (TechnologyType.mainFour.contains(colour)) counts.merge(colour, 1, Integer::sum);
            }
        }

        private Optional<TechnologyModel> filler(Collection<TechnologyType> eligible) {
            TechnologyModel best = null;
            int bestGap = Integer.MAX_VALUE;
            double bestScore = Double.NEGATIVE_INFINITY;
            for (TechnologyModel tech : available) {
                if (taken.contains(tech.getAlias()) || tech.isUnitUpgrade()) continue;
                if (tech.getTypes().stream().noneMatch(eligible::contains)) continue;
                int gap = TechColours.missing(seat, tech.getRequirements().orElse(""), counts, skips).values().stream()
                        .mapToInt(Integer::intValue)
                        .sum();
                double score = score(tech);
                if (best == null || gap < bestGap || gap == bestGap && score > bestScore) {
                    best = tech;
                    bestGap = gap;
                    bestScore = score;
                }
            }
            return Optional.ofNullable(best);
        }

        TechPath.Route finish() {
            int skipped = skips.values().stream().mapToInt(Integer::intValue).sum();
            if (skipped > 0) notes.add(skipped + " specialty skips counted");
            return new TechPath.Route(researches, List.copyOf(techs), List.copyOf(notes), List.copyOf(scarTechs));
        }
    }
}
