package ti4.ai.strategy;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.experimental.UtilityClass;
import ti4.ai.scoring.ObjectivePolicy;
import ti4.ai.secrets.SecretValue;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.image.Mapper;
import ti4.model.TechnologyModel;
import ti4.model.TechnologyModel.TechnologyType;
import ti4.service.info.ListPlayerInfoService;
import ti4.service.tech.ListTechService;

@UtilityClass
public class ResearchPolicy {

    static final double WORTH_PAYING_FOR = 4.0;
    static final double WORTH_FOLLOWING_FOR = 3.0;
    private static final double FACTION_TECH_VALUE = 3.5;
    private static final double DEFAULT_VALUE = 1.5;
    private static final double OBJECTIVE_WEIGHT = 4.0;
    private static final double PARTIAL_PROGRESS_SHARE = 0.4;
    private static final Map<String, Double> GENERIC_VALUE = Map.ofEntries(
            Map.entry("gd", 5.0),
            Map.entry("cv2", 4.5),
            Map.entry("st", 4.0),
            Map.entry("hm", 5.5),
            Map.entry("fl", 1.0),
            Map.entry("amd", 3.0),
            Map.entry("nm", 3.0),
            Map.entry("dxa", 0.5),
            Map.entry("ps", 3.0),
            Map.entry("aida", 3.0),
            Map.entry("asc", 3.5),
            Map.entry("dn2", 3.5),
            Map.entry("lwd", 3.0),
            Map.entry("ie", 3.0),
            Map.entry("sd2", 2.5),
            Map.entry("sar", 2.5),
            Map.entry("da", 2.5),
            Map.entry("dd2", 2.5),
            Map.entry("ff2", 2.5),
            Map.entry("bs", 2.5),
            Map.entry("md", 2.5),
            Map.entry("ws", 2.5),
            Map.entry("inf2", 2.0),
            Map.entry("pa", 2.0),
            Map.entry("sr", 2.0),
            Map.entry("pi", 2.0),
            Map.entry("pds2", 2.0),
            Map.entry("cr2", 2.0),
            Map.entry("sdn", 2.0),
            Map.entry("gls", 1.5),
            Map.entry("td", 1.5),
            Map.entry("det", 1.0),
            Map.entry("x89", 1.0));
    private static final Set<String> UNIT_UPGRADE_OBJECTIVES = Set.of("develop", "revolutionize");
    private static final Set<String> COLOUR_PAIR_OBJECTIVES = Set.of("diversify", "master_science");
    private static final String PRODUCE_EN_MASSE = "pem";
    private static final String SPACE_DOCK_UPGRADE = "sd2";
    private static final int DOCK_UPGRADE_PRODUCTION = 2;

    public static Optional<String> best(Game game, Player seat, Collection<String> candidates) {
        return candidates.stream()
                .filter(alias -> Mapper.getTech(alias) != null)
                .max(Comparator.comparingDouble((String alias) -> value(game, seat, alias))
                        .thenComparing(Comparator.naturalOrder()));
    }

    public static Optional<String> bestResearchable(Game game, Player seat) {
        List<String> researchable = new ArrayList<>();
        for (TechnologyType type : TechnologyType.values()) {
            for (TechnologyModel tech :
                    ListTechService.getAllTechOfAType(game, type.toString().toLowerCase(), seat, false, true)) {
                researchable.add(tech.getAlias());
            }
        }
        return best(game, seat, researchable);
    }

    public static double value(Game game, Player seat, String alias) {
        TechnologyModel tech = Mapper.getTech(alias);
        if (tech == null || seat.hasTech(alias)) return 0;
        double value = tech.getFaction().filter(faction -> !faction.isBlank()).isPresent()
                ? FACTION_TECH_VALUE
                : GENERIC_VALUE.getOrDefault(alias, DEFAULT_VALUE);
        return value + OBJECTIVE_WEIGHT * objectiveGain(game, seat, tech);
    }

    private static double objectiveGain(Game game, Player seat, TechnologyModel tech) {
        double gain = 0;
        for (String objective : objectivesInPlay(game, seat)) {
            int threshold = ListPlayerInfoService.getObjectiveThreshold(objective, game);
            if (threshold <= 0) continue;
            int before = progress(game, objective, seat, null);
            if (before >= threshold) continue;
            int after = progress(game, objective, seat, tech);
            if (after <= before) continue;
            double points = Math.max(1, ObjectivePolicy.victoryPoints(objective));
            gain += after >= threshold ? points : points * PARTIAL_PROGRESS_SHARE * after / threshold;
        }
        return gain;
    }

    private static List<String> objectivesInPlay(Game game, Player seat) {
        List<String> objectives = new ArrayList<>();
        for (String id : game.getRevealedPublicObjectives().keySet()) {
            if (!ObjectivePolicy.hasScored(game, seat, id)) objectives.add(id);
        }
        objectives.addAll(seat.getSecretsUnscored().keySet());
        return objectives;
    }

    private static int progress(Game game, String objective, Player seat, TechnologyModel adding) {
        if (PRODUCE_EN_MASSE.equals(objective)) {
            int production = ButtonHelper.checkHighestProductionSystem(seat, game);
            return adding != null && SPACE_DOCK_UPGRADE.equals(adding.getAlias())
                    ? production + DOCK_UPGRADE_PRODUCTION
                    : production;
        }
        if (UNIT_UPGRADE_OBJECTIVES.contains(objective)) {
            return ButtonHelper.getNumberOfUnitUpgrades(seat) + (adding != null && adding.isUnitUpgrade() ? 1 : 0);
        }
        if (COLOUR_PAIR_OBJECTIVES.contains(objective)) {
            int pairs = 0;
            for (TechnologyType type : TechnologyType.mainFour) {
                if (colourCount(seat, type, adding) >= 2) pairs++;
            }
            return pairs;
        }
        if ("mlp".equals(objective)) {
            int best = 0;
            for (TechnologyType type : TechnologyType.mainFour) best = Math.max(best, colourCount(seat, type, adding));
            return best;
        }
        if ("ans".equals(objective)) {
            boolean addsOwnFactionTech = adding != null
                    && adding.getFaction().filter(seat.getFaction()::equals).isPresent();
            return SecretValue.ownFactionTechnologies(seat) + (addsOwnFactionTech ? 1 : 0);
        }
        return 0;
    }

    private static int colourCount(Player seat, TechnologyType type, TechnologyModel adding) {
        int count = ButtonHelper.getNumberOfCertainTypeOfTech(seat, type);
        return adding != null && adding.getTypes().contains(type) ? count + 1 : count;
    }
}
