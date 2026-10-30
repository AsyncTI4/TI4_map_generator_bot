package ti4.ai.strategy;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import ti4.ai.scoring.ObjectivePolicy;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.model.Mapper;
import ti4.model.TechnologyModel;
import ti4.model.TechnologyModel.TechnologyType;
import ti4.service.tech.ListTechService;

public record ResearchPlan(
        String game,
        String faction,
        int round,
        String next,
        List<Step> steps,
        List<Goal> goals,
        Map<String, String> parts) {

    public enum Status {
        READY,
        SCAR_PURCHASE,
        FEASIBLE,
        TOO_LONG,
        UNREACHABLE
    }

    public record Step(String alias, List<String> reasons, boolean valued) {}

    public record Goal(String id, int points, int needed, Status status, List<String> notes) {}

    private record Candidate(String reason, double value, TechPath.Route route, boolean valued) {}

    static final double NEXT_BONUS = 1.0;
    static final double LATER_BONUS = 0.5;
    static final double ANTIMASS_REACH_VALUE = 3.0;
    private static final String NOTHING = "none";
    private static final int MAX_STEPS = 4;
    private static final int CACHE_SIZE = 2000;
    private static final Duration CACHE_IDLE = Duration.ofHours(1);
    private static final String GRAVITY_DRIVE = "gd";
    private static final String CARRIER_II = "cv2";
    private static final String TRANSIT_DIODES = "td";
    private static final String DESTROYER_II = "dd2";
    private static final Cache<String, ResearchPlan> CACHE = Caffeine.newBuilder()
            .maximumSize(CACHE_SIZE)
            .expireAfterAccess(CACHE_IDLE)
            .build();

    public static ResearchPlan of(Game game, Player seat) {
        Map<String, String> parts = signature(game, seat);
        String key = System.identityHashCode(game) + "|" + game.getName() + "|" + seat.getFaction() + "|"
                + String.join("|", parts.values());
        return CACHE.get(key, ignored -> build(game, seat, parts));
    }

    static void forgetAll() {
        CACHE.invalidateAll();
    }

    public String describe() {
        String path = steps.stream()
                .map(step -> step.alias() + " (" + String.join(", ", step.reasons()) + ")")
                .collect(Collectors.joining(", "));
        String objectives = goals.stream().map(ResearchPlan::describeGoal).collect(Collectors.joining("; "));
        return "ResearchPlan " + game + " " + faction + " r" + round + ": next " + next + " | path [" + path
                + "] | goals [" + objectives + "]";
    }

    public List<String> changedParts(ResearchPlan before) {
        return parts.entrySet().stream()
                .filter(part -> !part.getValue().equals(before.parts.get(part.getKey())))
                .map(Map.Entry::getKey)
                .toList();
    }

    Map<String, Double> bonuses(Collection<String> candidates) {
        Map<String, Double> bonuses = new LinkedHashMap<>();
        boolean nextTaken = false;
        for (Step step : steps) {
            if (!step.valued() || !candidates.contains(step.alias())) continue;
            bonuses.put(step.alias(), nextTaken ? LATER_BONUS : NEXT_BONUS);
            nextTaken = true;
        }
        return bonuses;
    }

    Optional<String> reasonFor(String alias) {
        return steps.stream()
                .filter(step -> step.alias().equals(alias))
                .findFirst()
                .map(step -> String.join(", ", step.reasons()));
    }

    private static String describeGoal(Goal goal) {
        String points = goal.points() > 0 ? " (" + goal.points() + " VP)" : "";
        String status =
                switch (goal.status()) {
                    case READY -> " ready to score";
                    case SCAR_PURCHASE -> " needs an Entropic Scar purchase";
                    case FEASIBLE -> " needs " + goal.needed() + " researches, feasible";
                    case TOO_LONG -> " needs " + goal.needed() + " researches, too long";
                    case UNREACHABLE -> " unreachable";
                };
        String notes = goal.notes().isEmpty() ? "" : " [" + String.join("; ", goal.notes()) + "]";
        return goal.id() + points + status + notes;
    }

    private static Map<String, String> signature(Game game, Player seat) {
        Map<String, String> parts = new LinkedHashMap<>();
        parts.put("round", String.valueOf(game.getRound()));
        parts.put("researches left", String.valueOf(TechPath.researchesLeft(game)));
        parts.put("can research", String.valueOf(canResearch(game, seat)));
        parts.put("techs", sorted(seat.getTechs()));
        parts.put("purged techs", sorted(seat.getPurgedTechs()));
        parts.put("secrets", sorted(seat.getSecretsUnscored().keySet()));
        parts.put("public objectives", publics(game, seat));
        parts.put("specialty planets", specialtyPlanets(game, seat));
        parts.put("scar purchase", String.valueOf(ScarToken.canBuy(game, seat)));
        parts.put(
                "synergies",
                seat.getSynergies().stream().map(TechnologyType::name).sorted().collect(Collectors.joining(",")));
        return parts;
    }

    private static boolean canResearch(Game game, Player seat) {
        return !StrategyCardRules.cannotResearch(seat) && !game.isTwilightsFallMode();
    }

    private static String sorted(Collection<String> values) {
        return values.stream().sorted().collect(Collectors.joining(","));
    }

    private static String publics(Game game, Player seat) {
        return game.getRevealedPublicObjectives().keySet().stream()
                .sorted()
                .map(id -> id + (ObjectivePolicy.hasScored(game, seat, id) ? "*" : ""))
                .collect(Collectors.joining(","));
    }

    private static String specialtyPlanets(Game game, Player seat) {
        return seat.getPlanets().stream()
                .filter(name -> {
                    Planet planet = game.getPlanetsInfo().get(name);
                    return planet != null && !planet.getTechSpecialities().isEmpty();
                })
                .sorted()
                .collect(Collectors.joining(","));
    }

    private static ResearchPlan build(Game game, Player seat, Map<String, String> parts) {
        if (!canResearch(game, seat)) {
            return new ResearchPlan(
                    game.getName(), seat.getFaction(), game.getRound(), NOTHING, List.of(), List.of(), parts);
        }
        int left = TechPath.researchesLeft(game);
        List<Goal> goals = new ArrayList<>();
        List<Candidate> candidates = new ArrayList<>();
        addObjectives(game, seat, left, goals, candidates);
        addAntimassReach(game, seat, left, goals, candidates);
        addReachReasons(game, seat, candidates);
        List<Step> steps = sequence(candidates, Math.min(left, MAX_STEPS));
        return new ResearchPlan(
                game.getName(), seat.getFaction(), game.getRound(), nextOf(seat, steps), steps, goals, parts);
    }

    private static String nextOf(Player seat, List<Step> steps) {
        return steps.stream()
                .map(Step::alias)
                .filter(alias -> {
                    TechnologyModel tech = Mapper.getTech(alias);
                    return tech != null && ListTechService.isTechResearchable(tech, seat);
                })
                .findFirst()
                .or(() -> steps.stream().map(Step::alias).findFirst())
                .orElse(NOTHING);
    }

    private static boolean routed(String objective) {
        return TechPath.follows(objective)
                || ResearchPolicy.UNIT_UPGRADE_OBJECTIVES.contains(objective)
                || ResearchPolicy.COLOUR_PAIR_OBJECTIVES.contains(objective);
    }

    private static void addObjectives(Game game, Player seat, int left, List<Goal> goals, List<Candidate> candidates) {
        for (String objective : ResearchPolicy.objectivesInPlay(game, seat)) {
            if (!routed(objective)) continue;
            int points = Math.max(1, ObjectivePolicy.victoryPoints(objective));
            Optional<TechPath.Route> route = TechPath.route(game, seat, objective);
            if (route.isEmpty()) {
                goals.add(new Goal(objective, points, 0, Status.UNREACHABLE, List.of()));
                continue;
            }
            int needed = route.get().researches();
            Status status = statusOf(route.get(), left);
            goals.add(new Goal(objective, points, needed, status, route.get().notes()));
            if (status == Status.FEASIBLE) {
                candidates.add(new Candidate(objective, points * ResearchPolicy.OBJECTIVE_WEIGHT, route.get(), true));
            }
        }
    }

    private static Status statusOf(TechPath.Route route, int left) {
        if (route.researches() == 0) return route.needsScarPurchase() ? Status.SCAR_PURCHASE : Status.READY;
        return route.researches() <= left ? Status.FEASIBLE : Status.TOO_LONG;
    }

    private static void addAntimassReach(
            Game game, Player seat, int left, List<Goal> goals, List<Candidate> candidates) {
        Optional<String> blocked = ReachLever.antimassTarget(game, seat);
        TechnologyModel deflectors = Mapper.getTech(ReachLever.ANTIMASS_DEFLECTORS);
        if (blocked.isEmpty() || deflectors == null || seat.getPurgedTechs().contains(deflectors.getAlias())) return;
        Optional<TechPath.Route> route = TechPath.routeTo(game, seat, deflectors);
        if (route.isEmpty()) return;
        String reason = "reach: asteroids block " + blocked.get();
        int needed = route.get().researches();
        boolean feasible = needed <= left;
        goals.add(new Goal(
                reason + ", amd",
                0,
                needed,
                feasible ? Status.FEASIBLE : Status.TOO_LONG,
                route.get().notes()));
        if (feasible) candidates.add(new Candidate(reason, ANTIMASS_REACH_VALUE, route.get(), true));
    }

    private static void addReachReasons(Game game, Player seat, List<Candidate> candidates) {
        addReach(
                seat,
                candidates,
                GRAVITY_DRIVE,
                "reach: custodians race",
                ResearchPolicy.custodiansRaceValue(game, seat, GRAVITY_DRIVE));
        addReach(
                seat,
                candidates,
                GRAVITY_DRIVE,
                "reach: opening",
                ResearchPolicy.openingReachValue(game, seat, GRAVITY_DRIVE));
        addReach(
                seat,
                candidates,
                CARRIER_II,
                "reach: fracture march",
                ResearchPolicy.fractureMarchValue(game, seat, CARRIER_II));
        addReach(seat, candidates, CARRIER_II, "reach: ground secret", groundSecret(game, seat, CARRIER_II));
        addReach(seat, candidates, TRANSIT_DIODES, "reach: ground secret", groundSecret(game, seat, TRANSIT_DIODES));
        addReach(seat, candidates, DESTROYER_II, "reach: barrage secret", barrageSecret(game, seat, DESTROYER_II));
    }

    private static double groundSecret(Game game, Player seat, String alias) {
        TechnologyModel tech = Mapper.getTech(alias);
        return tech == null || seat.hasTech(alias) ? 0 : ResearchPolicy.groundSecretValue(game, seat, tech);
    }

    private static double barrageSecret(Game game, Player seat, String alias) {
        TechnologyModel tech = Mapper.getTech(alias);
        return tech == null || seat.hasTech(alias) ? 0 : ResearchPolicy.barrageSecretValue(game, seat, tech);
    }

    private static void addReach(Player seat, List<Candidate> candidates, String alias, String reason, double value) {
        TechnologyModel tech = Mapper.getTech(alias);
        if (tech == null || seat.hasTech(alias) || value <= 0) return;
        candidates.add(new Candidate(reason, value, new TechPath.Route(1, List.of(tech), List.of()), false));
    }

    private static List<Step> sequence(List<Candidate> candidates, int cap) {
        Map<String, List<String>> reasons = new LinkedHashMap<>();
        Map<String, Boolean> valued = new LinkedHashMap<>();
        List<Candidate> ranked = candidates.stream()
                .sorted(Comparator.comparingDouble((Candidate candidate) -> -candidate.value()
                                / Math.max(1, candidate.route().researches()))
                        .thenComparing(Candidate::reason))
                .toList();
        for (Candidate candidate : ranked) {
            for (TechnologyModel tech : candidate.route().techs()) {
                String alias = tech.getAlias();
                if (!reasons.containsKey(alias) && reasons.size() >= cap) continue;
                List<String> own = reasons.computeIfAbsent(alias, ignored -> new ArrayList<>());
                if (!own.contains(candidate.reason())) own.add(candidate.reason());
                valued.merge(alias, candidate.valued(), Boolean::logicalOr);
            }
        }
        return reasons.entrySet().stream()
                .map(entry -> new Step(entry.getKey(), List.copyOf(entry.getValue()), valued.get(entry.getKey())))
                .toList();
    }
}
