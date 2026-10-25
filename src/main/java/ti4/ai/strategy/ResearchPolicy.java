package ti4.ai.strategy;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import lombok.experimental.UtilityClass;
import ti4.ai.brain.SearchMemo;
import ti4.ai.endurance.Endurance;
import ti4.ai.eval.CustodiansRace;
import ti4.ai.eval.FractureCampaign;
import ti4.ai.eval.GameHorizon;
import ti4.ai.eval.ValueScale;
import ti4.ai.scoring.ObjectivePolicy;
import ti4.ai.scoring.ObjectiveValue;
import ti4.ai.scoring.ScoringStakes;
import ti4.ai.tactical.GroundCampaign;
import ti4.ai.tactical.PrecisionRules;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.model.Mapper;
import ti4.model.TechnologyModel;
import ti4.model.TechnologyModel.TechnologyType;
import ti4.service.info.ListPlayerInfoService;
import ti4.service.tech.ListTechService;

@UtilityClass
public class ResearchPolicy {

    private static final String BEST_RESEARCHABLE_MEMO = "research.bestResearchable";
    public static final double WORTH_PAYING_FOR = 4.0;
    public static final double WORTH_FOLLOWING_FOR = 3.0;
    public static final double TECH_TEMPO =
            (ValueScale.RESOURCE * StrategyCardRules.FOLLOW_TECH_COST + CardValueScale.STRATEGY_TOKEN)
                    / WORTH_FOLLOWING_FOR;
    private static final double FACTION_TECH_VALUE = 2.0;
    private static final double DEFAULT_VALUE = 1.5;
    private static final Map<String, Double> FACTION_VALUE = Map.ofEntries(
            Map.entry("ac2", 4.5),
            Map.entry("sdn2", 4.0),
            Map.entry("nes", 4.0),
            Map.entry("exo2", 3.5),
            Map.entry("hcf2", 3.5),
            Map.entry("l4", 3.0),
            Map.entry("ers", 3.0),
            Map.entry("vpw", 3.0),
            Map.entry("ng", 3.0),
            Map.entry("mc", 3.0),
            Map.entry("pws2", 3.0),
            Map.entry("se2", 3.0),
            Map.entry("swa2", 3.0),
            Map.entry("helios2", 3.0),
            Map.entry("m2", 3.0),
            Map.entry("yso", 3.0),
            Map.entry("so2", 2.5),
            Map.entry("pfa", 2.5),
            Map.entry("lw2", 2.5),
            Map.entry("cl2", 2.5),
            Map.entry("ht2", 2.5),
            Map.entry("dt2", 2.5),
            Map.entry("exile2", 2.5),
            Map.entry("linkship2", 2.5),
            Map.entry("ic", 2.5),
            Map.entry("tp", 2.0),
            Map.entry("vw", 2.0),
            Map.entry("ah", 2.0),
            Map.entry("hydrothermal", 2.0),
            Map.entry("ffac2", 2.0),
            Map.entry("pm", 2.0),
            Map.entry("nf", 1.5),
            Map.entry("scc", 1.5),
            Map.entry("is", 1.5),
            Map.entry("it", 1.0),
            Map.entry("qdn", 1.0),
            Map.entry("vtx", 1.0),
            Map.entry("lgf", 1.0),
            Map.entry("tcs", 1.0),
            Map.entry("gr", 1.0),
            Map.entry("htp", 1.0),
            Map.entry("radical", 1.0),
            Map.entry("planesplitter-obs", 1.0),
            Map.entry("mr", 1.0),
            Map.entry("mi", 1.0),
            Map.entry("cm", 1.0),
            Map.entry("as", 1.0),
            Map.entry("wg", 1.0),
            Map.entry("nanomachines", 1.0),
            Map.entry("executiveorder", 1.0),
            Map.entry("asn", 1.0),
            Map.entry("ds", 1.0),
            Map.entry("so", 1.0),
            Map.entry("parasite-obs", 1.0),
            Map.entry("subatomic", 1.0),
            Map.entry("sc", 1.0));
    static final double OBJECTIVE_WEIGHT =
            ObjectiveValue.VICTORY_POINT_VALUE * WORTH_PAYING_FOR / StrategyCardRules.SECOND_TECH_COST;
    private static final double PARTIAL_PROGRESS_SHARE = 0.4;
    private static final Map<String, Double> GENERIC_VALUE = Map.ofEntries(
            Map.entry("gd", 5.0),
            Map.entry("cv2", 4.5),
            Map.entry("st", 4.0),
            Map.entry("hm", 5.5),
            Map.entry("amd", 3.0),
            Map.entry("fl", 3.0),
            Map.entry("nm", 3.0),
            Map.entry("dxa", 0.5),
            Map.entry("ps", 3.0),
            Map.entry("aida", 3.0),
            Map.entry("asc", 3.5),
            Map.entry("x89c4", 3.5),
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
            Map.entry("sr", 2.5),
            Map.entry("inf2", 2.0),
            Map.entry("pa", 2.0),
            Map.entry("pi", 2.0),
            Map.entry("det", 2.0),
            Map.entry("pds2", 2.0),
            Map.entry("cr2", 2.0),
            Map.entry("sdn", 2.0),
            Map.entry("gls", 1.5),
            Map.entry("td", 1.0),
            Map.entry("x89", 1.0));
    static final Set<String> UNIT_UPGRADE_OBJECTIVES = Set.of("develop", "revolutionize");
    static final Set<String> COLOUR_PAIR_OBJECTIVES = Set.of("diversify", "master_science");
    private static final Set<String> STATUS_PHASE_INCOME = Set.of("hm", "nm");
    private static final double OPENING_STATUS_PHASES = 5;
    private static final String GRAVITY_DRIVE = "gd";
    private static final String CARRIER_II = "cv2";
    private static final double FRACTURE_MARCH_VALUE = 1.5;
    private static final int FRACTURE_MARCH_ROUNDS = 3;
    private static final double GRAVITY_DRIVE_RACE_SHARE = 0.5;
    private static final String PRODUCE_EN_MASSE = "pem";
    static final int PAIR = 2;
    private static final double RESEARCHES_PER_ROUND = 1.5;
    private static final String SPACE_DOCK_UPGRADE = "sd2";
    private static final double STEPPING_STONE_SHARE = 0.3;
    private static final Set<String> TRANSIT_DIODES = Set.of("td", "absol_td");
    private static final List<String> GROUND_SECRETS = List.of("mtm", "otf");
    private static final double GROUND_SECRET_SHARE = 0.4;
    private static final double GROUND_SPEED_UP_SHARE = 0.75;
    private static final Set<String> DESTROYER_UPGRADES = Set.of("dd2", "absol_dd2");
    private static final double BARRAGE_SECRET_SHARE = 0.3;

    public static Optional<String> best(Game game, Player seat, Collection<String> candidates) {
        List<String> valid = candidates.stream()
                .filter(alias -> Mapper.getTech(alias) != null)
                .distinct()
                .toList();
        if (valid.isEmpty()) return Optional.empty();
        Map<String, Double> bonuses = ResearchPlan.of(game, seat).bonuses(valid);
        Map<String, Double> scores = new HashMap<>();
        for (String alias : valid) {
            scores.put(alias, researchValue(game, seat, alias) + bonuses.getOrDefault(alias, 0.0));
        }
        return valid.stream()
                .max(Comparator.comparingDouble((String alias) -> scores.get(alias))
                        .thenComparing(Comparator.naturalOrder()));
    }

    public static double researchValue(Game game, Player seat, String alias) {
        TechnologyModel tech = Mapper.getTech(alias);
        if (tech == null || seat.hasTech(alias)) return 0;
        return value(game, seat, alias)
                - PrerequisiteSkips.of(game, seat, tech).valueLost()
                + ResearchPrior.nudge(seat.getFaction(), alias);
    }

    public static double freeResearchWorth(Game game, Player seat) {
        return bestResearchable(game, seat)
                .map(tech -> TECH_TEMPO * researchValue(game, seat, tech)
                        + ScoringStakes.scoreValue(game, seat, publicPointsCompletedBy(game, seat, tech)))
                .orElse(0.0);
    }

    public static Optional<String> bestResearchable(Game game, Player seat) {
        return SearchMemo.remember(() -> pickBestResearchable(game, seat), BEST_RESEARCHABLE_MEMO, seat);
    }

    private static Optional<String> pickBestResearchable(Game game, Player seat) {
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
        return baseValue(tech) * statusPhaseShare(game, alias)
                + OBJECTIVE_WEIGHT * objectiveGain(game, seat, tech)
                + steppingStoneValue(game, seat, tech)
                + custodiansRaceValue(game, seat, alias)
                + openingReachValue(game, seat, alias)
                + fractureMarchValue(game, seat, alias)
                + groundSecretValue(game, seat, tech)
                + barrageSecretValue(game, seat, tech)
                + Endurance.researchValue(seat, alias);
    }

    static double fractureMarchValue(Game game, Player seat, String alias) {
        if (!CARRIER_II.equals(alias) || GameHorizon.roundsLeft(game) < FRACTURE_MARCH_ROUNDS) return 0;
        if (FractureCampaign.dock(game, seat).isEmpty()) return 0;
        return FRACTURE_MARCH_VALUE * FractureCampaign.routeShare(game, seat);
    }

    static double barrageSecretValue(Game game, Player seat, TechnologyModel tech) {
        if (!DESTROYER_UPGRADES.contains(tech.getAlias())
                || !PrecisionRules.held(seat)
                || !ListTechService.isTechResearchable(tech, seat)) {
            return 0;
        }
        return BARRAGE_SECRET_SHARE * ScoringStakes.scoreValue(game, seat, 1);
    }

    static double groundSecretValue(Game game, Player seat, TechnologyModel tech) {
        boolean diodes = TRANSIT_DIODES.contains(tech.getAlias());
        if (!diodes && !CARRIER_II.equals(tech.getAlias()) || !ListTechService.isTechResearchable(tech, seat)) return 0;
        GroundCampaign campaign = GroundCampaign.of(game, seat);
        double speedUp = GROUND_SECRETS.stream()
                .filter(seat.getSecretsUnscored()::containsKey)
                .mapToDouble(secret -> groundSpeedUp(campaign, secret, diodes))
                .sum();
        return speedUp * GROUND_SECRET_SHARE * ScoringStakes.scoreValue(game, seat, 1);
    }

    private static double groundSpeedUp(GroundCampaign campaign, String secret, boolean diodes) {
        GroundCampaign.Eta now = campaign.eta(secret);
        GroundCampaign.Eta upgraded = campaign.eta(secret, !diodes, diodes);
        if (!now.feasible() && upgraded.feasible()) return 1.0;
        return now.rounds() - upgraded.rounds() >= 1 ? GROUND_SPEED_UP_SHARE : 0;
    }

    static double bestValueAsTechnologyHolder(Game game, Player seat) {
        double best = bestResearchable(game, seat)
                .map(alias -> value(game, seat, alias))
                .orElse(0.0);
        double holderReach = OpeningOutlook.gravityDriveGainAsTechnologyHolder(game, seat);
        if (holderReach <= openingReachValue(game, seat, GRAVITY_DRIVE)) return best;
        double drive = value(game, seat, GRAVITY_DRIVE) - openingReachValue(game, seat, GRAVITY_DRIVE) + holderReach;
        return Math.max(best, drive);
    }

    static double openingReachValue(Game game, Player seat, String alias) {
        if (!GRAVITY_DRIVE.equals(alias)) return 0;
        return OpeningOutlook.gain(game, seat, OpeningOutlook.Boost.GRAVITY_DRIVE);
    }

    static double custodiansRaceValue(Game game, Player seat, String alias) {
        if (!GRAVITY_DRIVE.equals(alias) || !CustodiansRace.open(game)) return 0;
        CustodiansRace.Snapshot race = CustodiansRace.of(game, seat);
        double withDrive =
                race.raceValue(seat, race.arrivalWith(seat, CustodiansRace.Drive.LIKELY), CustodiansRace.Order.UNKNOWN);
        double without =
                race.raceValue(seat, race.arrivalWith(seat, CustodiansRace.Drive.OWNED), CustodiansRace.Order.UNKNOWN);
        return GRAVITY_DRIVE_RACE_SHARE * Math.max(0, withDrive - without);
    }

    private static double statusPhaseShare(Game game, String alias) {
        if (!STATUS_PHASE_INCOME.contains(alias)) return 1;
        return Math.min(1, (double) GameHorizon.roundsLeft(game) / OPENING_STATUS_PHASES);
    }

    public static double heldValue(String alias) {
        TechnologyModel tech = Mapper.getTech(alias);
        return tech == null ? 0 : baseValue(tech);
    }

    public static int publicPointsCompletedBy(Game game, Player player, String alias) {
        TechnologyModel tech = Mapper.getTech(alias);
        if (tech == null) return 0;
        return unscoredPublicsWithAThreshold(game, player)
                .filter(id -> {
                    int threshold = ListPlayerInfoService.getObjectiveThreshold(id, game);
                    return progress(game, id, player, null) < threshold
                            && progress(game, id, player, tech) >= threshold;
                })
                .mapToInt(id -> Math.max(1, ObjectivePolicy.victoryPoints(id)))
                .max()
                .orElse(0);
    }

    public static int publicPointsOneTechShort(Game game, Player player) {
        return unscoredPublicsWithAThreshold(game, player)
                .filter(id -> {
                    int threshold = ListPlayerInfoService.getObjectiveThreshold(id, game);
                    return progress(game, id, player, null) < threshold
                            && techsStillNeeded(id, player, null, threshold) == 1;
                })
                .mapToInt(id -> Math.max(1, ObjectivePolicy.victoryPoints(id)))
                .max()
                .orElse(0);
    }

    private static Stream<String> unscoredPublicsWithAThreshold(Game game, Player player) {
        return game.getRevealedPublicObjectives().keySet().stream()
                .filter(id -> !ObjectivePolicy.hasScored(game, player, id))
                .filter(id -> ListPlayerInfoService.getObjectiveThreshold(id, game) > 0);
    }

    static double baseValue(TechnologyModel tech) {
        String alias = tech.getAlias();
        return isFactionTech(tech)
                ? FACTION_VALUE.getOrDefault(alias, FACTION_TECH_VALUE)
                : GENERIC_VALUE.getOrDefault(alias, DEFAULT_VALUE);
    }

    private static double steppingStoneValue(Game game, Player seat, TechnologyModel tech) {
        if (StrategyCardRules.cannotResearch(seat)) return 0;
        double best = 0;
        for (String alias : game.getTechnologyDeck()) {
            TechnologyModel next = Mapper.getTech(alias);
            if (next == null || next == tech || seat.hasTech(alias)) continue;
            if (isFactionTech(next) && !seat.getNotResearchedFactionTechs().contains(alias)) continue;
            if (missingPrerequisites(seat, next, null) != 1 || missingPrerequisites(seat, next, tech) != 0) continue;
            if (ListTechService.isTechResearchable(next, seat)) continue;
            best = Math.max(best, baseValue(next));
        }
        return STEPPING_STONE_SHARE * best;
    }

    static int missingPrerequisites(Player seat, TechnologyModel next, TechnologyModel adding) {
        return TechColours.missing(seat, next.getRequirements().orElse(""), TechColours.owned(seat, adding), Map.of())
                .values()
                .stream()
                .mapToInt(Integer::intValue)
                .sum();
    }

    private static boolean isFactionTech(TechnologyModel tech) {
        return tech.getFaction().filter(faction -> !faction.isBlank()).isPresent();
    }

    private static double objectiveGain(Game game, Player seat, TechnologyModel tech) {
        double gain = 0;
        int laterResearches = researchesLeft(game) - 1;
        for (String objective : objectivesInPlay(game, seat)) {
            if (TechPath.follows(objective)) {
                gain += TechPath.gain(game, seat, objective, tech)
                        * Math.max(1, ObjectivePolicy.victoryPoints(objective));
                continue;
            }
            int threshold = ListPlayerInfoService.getObjectiveThreshold(objective, game);
            if (threshold <= 0) continue;
            int before = progress(game, objective, seat, null);
            if (before >= threshold) continue;
            int after = progress(game, objective, seat, tech);
            if (after <= before) continue;
            if (after < threshold && techsStillNeeded(objective, seat, tech, threshold) > laterResearches) continue;
            double points = Math.max(1, ObjectivePolicy.victoryPoints(objective));
            gain += after >= threshold ? points : points * PARTIAL_PROGRESS_SHARE * after / threshold;
        }
        return gain;
    }

    static int researchesLeft(Game game) {
        return Math.max(1, (int) Math.floor(GameHorizon.roundsLeft(game) * RESEARCHES_PER_ROUND));
    }

    static boolean withinReach(Game game, Player seat, String objective) {
        if (ObjectivePolicy.hasScored(game, seat, objective)) return false;
        int threshold = ListPlayerInfoService.getObjectiveThreshold(objective, game);
        return threshold <= 0 || techsStillNeeded(objective, seat, null, threshold) <= researchesLeft(game);
    }

    private static int techsStillNeeded(
            String objective, Player seat, @Nullable TechnologyModel adding, int threshold) {
        if (UNIT_UPGRADE_OBJECTIVES.contains(objective)) {
            boolean upgrade = adding != null && adding.isUnitUpgrade();
            return threshold - ButtonHelper.getNumberOfUnitUpgrades(seat) - (upgrade ? 1 : 0);
        }
        List<Integer> colours = TechnologyType.mainFour.stream()
                .map(type -> colourCount(seat, type, adding))
                .sorted(Comparator.reverseOrder())
                .toList();
        if (COLOUR_PAIR_OBJECTIVES.contains(objective)) {
            return colours.stream()
                    .limit(threshold)
                    .mapToInt(count -> Math.max(0, PAIR - count))
                    .sum();
        }
        return 0;
    }

    static List<String> objectivesInPlay(Game game, Player seat) {
        List<String> objectives = new ArrayList<>();
        for (String id : game.getRevealedPublicObjectives().keySet()) {
            if (!ObjectivePolicy.hasScored(game, seat, id)) objectives.add(id);
        }
        objectives.addAll(seat.getSecretsUnscored().keySet());
        return objectives;
    }

    private static int progress(Game game, String objective, Player seat, TechnologyModel adding) {
        if (PRODUCE_EN_MASSE.equals(objective)) {
            return adding != null && SPACE_DOCK_UPGRADE.equals(adding.getAlias())
                    ? MassProduction.bestWithDockUpgrade(game, seat)
                    : ButtonHelper.checkHighestProductionSystem(seat, game);
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
        return 0;
    }

    private static int colourCount(Player seat, TechnologyType type, TechnologyModel adding) {
        int count = ButtonHelper.getNumberOfCertainTypeOfTech(seat, type);
        return adding != null && adding.getTypes().contains(type) ? count + 1 : count;
    }
}
