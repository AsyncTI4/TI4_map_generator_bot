package ti4.ai.strategy;

import java.util.Collection;
import java.util.Comparator;
import java.util.Optional;
import java.util.Set;
import lombok.experimental.UtilityClass;
import ti4.game.Game;
import ti4.game.Player;
import ti4.image.Mapper;
import ti4.model.TechnologyModel;

@UtilityClass
public class CopiedTechPolicy {

    private static final Set<String> ASSIMILATORS = Set.of("vax", "vay");
    private static final int ASSIMILATOR_TOKENS = 2;
    private static final double PREREQUISITE_VALUE = 0.3;

    public static Optional<String> best(Game game, Player seat, Collection<String> aliases) {
        return aliases.stream()
                .filter(alias -> allowed(seat, alias))
                .max(Comparator.comparingDouble((String alias) -> value(game, seat, alias))
                        .thenComparing(Comparator.naturalOrder()));
    }

    public static double value(Game game, Player seat, String alias) {
        TechnologyModel tech = Mapper.getTech(alias);
        if (tech == null || !allowed(seat, alias)) return 0;
        double prerequisites = tech.getRequirements().map(String::length).orElse(0);
        return ResearchPolicy.value(game, seat, alias) + PREREQUISITE_VALUE * prerequisites;
    }

    private static boolean allowed(Player seat, String alias) {
        TechnologyModel tech = Mapper.getTech(alias);
        if (tech == null || seat.hasTech(alias)) return false;
        if (!isFactionTech(tech)) return true;
        return copiedFactionTechs(seat) < ASSIMILATOR_TOKENS;
    }

    private static long copiedFactionTechs(Player seat) {
        return seat.getTechs().stream()
                .filter(owned -> !ASSIMILATORS.contains(owned))
                .map(Mapper::getTech)
                .filter(model -> model != null && isFactionTech(model))
                .filter(model -> !model.getFaction().orElse("").equals(seat.getFaction()))
                .count();
    }

    private static boolean isFactionTech(TechnologyModel tech) {
        return tech.getFaction().filter(faction -> !faction.isBlank()).isPresent();
    }
}
