package ti4.ai;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import ti4.game.Player;

@UtilityClass
public class AiExperiments {

    public static final String AB_PROPERTY = "ai.selfplay.ab";
    public static final String DISABLED_KEY = "aiExperimentsOff";

    public enum Experiment {
        READY_PUBLIC_POINTS,
        WIN_THREAT_FEASIBLE,
        WIN_AWARE_POINTS,
        AGENDA_SABOTAGE,
        IMPERIAL_POINTS,
        CANNON_COVER_RISK,
        BLOCKADE_GROUND,
        EXPLOIT_GUARDS,
        SPEAKER_SALE,
        WIN_BREAK_DENIAL,
        ATTACK_MEMORY,
        PROMISE_DEALS,
        AGENDA_VALUE_TABLE,
        GALACTIC_THREAT_COST,
        AGENDA_HELD_BALLOTS,
        CONDITIONAL_VOTE_DEALS,
        DISCARD_BOUNTIES,
        AGENDA_ABSTAIN_CHOICE,
        GALACTIC_THREAT_TIMING,
        RIVAL_RIDER_STAKES,
        RIDER_EXTORTION,
        IMPERIAL_RIDER_HOLD,
        GALACTIC_THREAT_BY_VOTERS,
        PAY_THE_ANSWERER,
        BOAT_FLOAT,
        AVOIDANCE_BRIBES,
        DESPERATION_PUBLIC,
        DESPERATION_STATUS_SECRET,
        DESPERATION_ACTION_SECRET,
        RELATIONS_ANGER,
        VOTE_ANGER,
        STRENGTH_WEIGHTED_ANGER,
        ANGER_SPREAD,
        POOLED_BOOST_RESPLIT,
        STYX_TAKE,
        TECH_FOLLOW_KEEP,
        DIPLOMACY_READIED_PLANNING,
        XXCHA_LEADER_READIES
    }

    public static final String OFF_PROPERTY = "ai.experiments.off";
    private static final Set<Experiment> OFF_EVERYWHERE =
            parse(System.getProperty(OFF_PROPERTY)).orElse(EnumSet.noneOf(Experiment.class));

    public static final String ENABLED_KEY = "aiExperimentsOn";
    private static final Set<Experiment> OFF_BY_DEFAULT = EnumSet.of(
            Experiment.DESPERATION_PUBLIC, Experiment.DESPERATION_STATUS_SECRET, Experiment.DESPERATION_ACTION_SECRET);
    private static final Set<Experiment> UNDER_TEST = abExperiments().orElse(EnumSet.noneOf(Experiment.class));

    public static boolean enabled(Player seat, Experiment experiment) {
        if (OFF_EVERYWHERE.contains(experiment)) return false;
        if (OFF_BY_DEFAULT.contains(experiment) && !switchedOn(seat, experiment)) return false;
        return !listed(seat.getStoredValue(DISABLED_KEY), experiment);
    }

    public static void enable(Player seat, Set<Experiment> experiments) {
        seat.setStoredValue(
                ENABLED_KEY, experiments.stream().map(Experiment::name).collect(Collectors.joining(",")));
    }

    private static boolean switchedOn(Player seat, Experiment experiment) {
        return UNDER_TEST.contains(experiment) || listed(seat.getStoredValue(ENABLED_KEY), experiment);
    }

    private static boolean listed(String names, Experiment experiment) {
        return !names.isEmpty() && Arrays.asList(names.split(",")).contains(experiment.name());
    }

    public static void disable(Player seat, Set<Experiment> experiments) {
        if (experiments.isEmpty()) {
            seat.setStoredValue(DISABLED_KEY, "-");
            return;
        }
        seat.setStoredValue(
                DISABLED_KEY, experiments.stream().map(Experiment::name).collect(Collectors.joining(",")));
    }

    public static boolean isControlSeat(Player seat) {
        return !seat.getStoredValue(DISABLED_KEY).isEmpty();
    }

    public static Set<Experiment> offByDefault() {
        return EnumSet.copyOf(OFF_BY_DEFAULT);
    }

    public static Optional<Set<Experiment>> abExperiments() {
        return parse(System.getProperty(AB_PROPERTY));
    }

    private static Optional<Set<Experiment>> parse(String value) {
        if (value == null || value.isBlank()) return Optional.empty();
        if ("all".equalsIgnoreCase(value.trim())) return Optional.of(EnumSet.allOf(Experiment.class));
        if ("none".equalsIgnoreCase(value.trim())) return Optional.of(EnumSet.noneOf(Experiment.class));
        Set<Experiment> experiments = EnumSet.noneOf(Experiment.class);
        for (String name : value.split(","))
            experiments.add(Experiment.valueOf(name.trim().toUpperCase()));
        return Optional.of(experiments);
    }
}
