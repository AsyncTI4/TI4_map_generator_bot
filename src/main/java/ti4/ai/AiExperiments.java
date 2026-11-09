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
        WIN_THREAT_FEASIBLE,
        WIN_AWARE_POINTS,
        AGENDA_SABOTAGE,
        IMPERIAL_POINTS,
        CANNON_COVER_RISK,
        BLOCKADE_GROUND,
        EXPLOIT_GUARDS,
        SPEAKER_SALE,
        ATTACK_MEMORY
    }

    public static final String OFF_PROPERTY = "ai.experiments.off";
    private static final Set<Experiment> OFF_EVERYWHERE =
            parse(System.getProperty(OFF_PROPERTY)).orElse(EnumSet.noneOf(Experiment.class));

    public static boolean enabled(Player seat, Experiment experiment) {
        if (OFF_EVERYWHERE.contains(experiment)) return false;
        String disabled = seat.getStoredValue(DISABLED_KEY);
        if (disabled.isEmpty()) return true;
        return !Arrays.asList(disabled.split(",")).contains(experiment.name());
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
