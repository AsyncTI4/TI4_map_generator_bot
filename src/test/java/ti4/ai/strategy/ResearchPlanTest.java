package ti4.ai.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.eval.MovementGraph;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.model.MapPositionMapper;
import ti4.model.Mapper;
import ti4.model.TechnologyModel;
import ti4.model.TechnologyModel.TechnologyType;
import ti4.model.TileHelper;
import ti4.service.tech.ListTechService;
import ti4.testUtils.BaseTi4Test;

// Sol starts with Antimass Deflectors (blue) and Neural Motivator (green). Its faction technologies are Advanced
// Carrier II (BB) and Spec Ops II (GG), both unit upgrades, so they add no colour of their own. A fresh ten point
// game leaves five rounds, so five researches.
class ResearchPlanTest extends BaseTi4Test {

    private AiTestGame test;

    @BeforeEach
    void setUp() {
        ResearchPlan.forgetAll();
        test = new AiTestGame();
        test.sol.setSecret("ans");
    }

    @AfterEach
    void tearDown() {
        ResearchPlan.forgetAll();
    }

    // The cheapest Adapt New Strategies route is the two faction technologies and one filler of each missing colour,
    // named, and costs what TechPath says it costs.
    @Test
    void theRouteNamesTheFactionTechnologiesAndTheFillersThatUnlockThem() {
        TechPath.Route route = TechPath.route(test.game, test.sol, "ans").orElseThrow();

        assertThat(route.researches()).isEqualTo(4);
        assertThat(route.researches())
                .isEqualTo(TechPath.researchesNeeded(test.game, test.sol, "ans").getAsInt());
        assertThat(aliases(route)).contains("ac2", "so2").hasSize(4);
        assertThat(route.techs().stream().filter(tech -> !tech.isUnitUpgrade()))
                .hasSize(2)
                .allSatisfy(tech -> assertThat(tech.getTypes()).anyMatch(TechnologyType.mainFour::contains));
    }

    // A filler comes before the faction technology it unlocks.
    @Test
    void fillersComeBeforeTheTechnologyTheyUnlock() {
        List<String> order = aliases(TechPath.route(test.game, test.sol, "ans").orElseThrow());

        assertThat(order.indexOf("ac2")).isGreaterThan(0);
        assertThat(order.indexOf("so2")).isGreaterThan(0);
        assertThat(order.getFirst()).isNotIn("ac2", "so2");
    }

    // A specialty planet stands in for a filler, so the route is one shorter and names one filler less.
    @Test
    void aSpecialtyPlanetShortensTheRoute() {
        test.place("22", "101");
        test.sol.addPlanet("tarmann");

        TechPath.Route route = TechPath.route(test.game, test.sol, "ans").orElseThrow();

        assertThat(route.researches()).isEqualTo(3);
        assertThat(route.techs()).hasSize(3);
        assertThat(route.notes()).anyMatch(note -> note.contains("specialty skips"));
    }

    // Develop Weaponry wants two unit upgrades; the route counts the colours the upgrades require, which the greedy
    // objective term ignores.
    @Test
    void aUnitUpgradeRouteIncludesThePrerequisites() {
        test.game.getRevealedPublicObjectives().put("develop", 1);

        TechPath.Route route = TechPath.route(test.game, test.sol, "develop").orElseThrow();

        assertThat(route.techs().stream().filter(TechnologyModel::isUnitUpgrade))
                .hasSize(2);
        assertThat(route.researches()).isEqualTo(route.techs().size());
        assertThat(route.researches()).isGreaterThanOrEqualTo(2);
    }

    // Diversify Research wants two technologies in each of two colours. Sol has one green and one blue, so one more
    // of each, named.
    @Test
    void aColourPairRouteNamesOneFillerPerMissingTechnology() {
        test.game.getRevealedPublicObjectives().put("diversify", 1);

        TechPath.Route route = TechPath.route(test.game, test.sol, "diversify").orElseThrow();

        assertThat(route.researches()).isEqualTo(2);
        assertThat(colours(route)).containsExactlyInAnyOrder(TechnologyType.BIOTIC, TechnologyType.PROPULSION);
    }

    // Master the Laws of Physics needs four of one colour: Sol has one of each, so three more of the closest colour.
    @Test
    void theLawsOfPhysicsRouteNamesThreeFillersOfOneColour() {
        test.game.getRevealedPublicObjectives().put("mlp", 1);

        TechPath.Route route = TechPath.route(test.game, test.sol, "mlp").orElseThrow();

        assertThat(route.researches()).isEqualTo(3);
        assertThat(colours(route)).hasSize(3).containsOnly(colours(route).getFirst());
    }

    // Master the Sciences is far more than five researches from a fresh Sol, so it is listed as too long and leaves
    // the path alone.
    @Test
    void anObjectiveTooFarToFinishIsListedButNotPlanned() {
        test.game.getRevealedPublicObjectives().put("master_science", 1);

        ResearchPlan plan = ResearchPlan.of(test.game, test.sol);

        assertThat(plan.goals()).anySatisfy(goal -> {
            assertThat(goal.id()).isEqualTo("master_science");
            assertThat(goal.status()).isEqualTo(ResearchPlan.Status.TOO_LONG);
        });
        assertThat(plan.steps()).noneMatch(step -> step.reasons().contains("master_science"));
        assertThat(plan.describe()).contains("master_science").contains("too long");
    }

    @Test
    void thePlanFollowsTheAdaptNewStrategiesRoute() {
        ResearchPlan plan = ResearchPlan.of(test.game, test.sol);

        assertThat(plan.steps().stream().map(ResearchPlan.Step::alias)).contains("ac2", "so2");
        assertThat(plan.steps()).allSatisfy(step -> assertThat(step.reasons()).contains("ans"));
        assertThat(plan.goals()).anySatisfy(goal -> {
            assertThat(goal.id()).isEqualTo("ans");
            assertThat(goal.status()).isEqualTo(ResearchPlan.Status.FEASIBLE);
            assertThat(goal.needed()).isEqualTo(4);
        });
        assertThat(plan.next()).isNotEqualTo("none");
    }

    // Only the researches left can be spent: with the leader on 8 of 10 points one research remains.
    @Test
    void anObjectiveNeedingMoreResearchesThanRemainAreLeftOut() {
        test.points(test.nekro, 8);

        ResearchPlan plan = ResearchPlan.of(test.game, test.sol);

        assertThat(plan.steps()).isEmpty();
        assertThat(plan.goals()).anyMatch(goal -> goal.status() == ResearchPlan.Status.TOO_LONG);
    }

    @Test
    void nothingHeldOrRevealedLeavesAnEmptyPlanAndTheGreedyChoice() {
        test.sol.removeSecret(test.sol.getSecretsUnscored().get("ans"));

        ResearchPlan plan = ResearchPlan.of(test.game, test.sol);

        assertThat(plan.steps()).isEmpty();
        assertThat(plan.goals()).isEmpty();
        assertThat(plan.next()).isEqualTo("none");
        List<String> researchable = researchable();
        Optional<String> greedy = researchable.stream()
                .max(Comparator.comparingDouble(
                                (String alias) -> ResearchPolicy.researchValue(test.game, test.sol, alias))
                        .thenComparing(Comparator.naturalOrder()));
        assertThat(ResearchPolicy.best(test.game, test.sol, researchable)).isEqualTo(greedy);
    }

    // The bonus is for choosing between technologies, not for how much a technology is worth, so the values every
    // spending threshold reads are the same with and without a plan.
    @Test
    void theBonusLeavesTheResearchValuesAlone() {
        String onPlan = ResearchPlan.of(test.game, test.sol).next();

        double value = ResearchPolicy.value(test.game, test.sol, onPlan);
        double researchValue = ResearchPolicy.researchValue(test.game, test.sol, onPlan);

        assertThat(researchValue)
                .isEqualTo(value
                        - PrerequisiteSkips.of(test.game, test.sol, Mapper.getTech(onPlan))
                                .valueLost()
                        + ResearchPrior.nudge("sol", onPlan));
        ResearchPolicy.bestResearchable(test.game, test.sol);
        assertThat(ResearchPolicy.researchValue(test.game, test.sol, onPlan)).isEqualTo(researchValue);
    }

    // Bio-Stims is on the Adapt New Strategies route; Sling Relay is not and is worth a little more. The plan bonus
    // for the next technology on the route settles it, which the plain values would not.
    @Test
    void theNextTechnologyOnThePlanBeatsASlightlyBetterOffPlanOne() {
        double onPlan = ResearchPolicy.researchValue(test.game, test.sol, "bs");
        double offPlan = ResearchPolicy.researchValue(test.game, test.sol, "sr");
        assertThat(planned("bs")).isTrue();
        assertThat(planned("sr")).isFalse();
        assertThat(offPlan).isBetween(onPlan, onPlan + ResearchPlan.NEXT_BONUS);

        assertThat(ResearchPolicy.best(test.game, test.sol, List.of("sr", "bs")))
                .hasValue("bs");
    }

    // With Master the Laws of Physics held the route is Gravity Drive, Sling Relay and Light/Wave Deflector. Hyper
    // Metabolism is off the route but worth more than Sling Relay and the bonus together, so it still wins.
    @Test
    void aMuchBetterOffPlanTechnologyStillWins() {
        test.sol.removeSecret(test.sol.getSecretsUnscored().get("ans"));
        test.sol.setSecret("mlp");
        double onPlan = ResearchPolicy.researchValue(test.game, test.sol, "sr");
        double offPlan = ResearchPolicy.researchValue(test.game, test.sol, "hm");
        assertThat(planned("sr")).isTrue();
        assertThat(planned("hm")).isFalse();
        assertThat(offPlan).isGreaterThan(onPlan + ResearchPlan.NEXT_BONUS);

        assertThat(ResearchPolicy.best(test.game, test.sol, List.of("sr", "hm")))
                .hasValue("hm");
    }

    @Test
    void anUnchangedBoardHitsTheCacheAndAFleetMoveDoesNot() {
        ResearchPlan first = ResearchPlan.of(test.game, test.sol);
        assertThat(ResearchPlan.of(test.game, test.sol)).isSameAs(first);

        Tile tile = test.place("26", AiTestGame.HOME);
        test.units(tile, "space", test.sol, UnitType.Carrier, 1);

        assertThat(ResearchPlan.of(test.game, test.sol)).isSameAs(first);
    }

    @Test
    void drawingASecretChangesThePlanAndNamesTheSecrets() {
        ResearchPlan before = ResearchPlan.of(test.game, test.sol);

        test.sol.setSecret("mlp");
        ResearchPlan after = ResearchPlan.of(test.game, test.sol);

        assertThat(after).isNotSameAs(before);
        assertThat(after.changedParts(before)).containsExactly("secrets");
    }

    @Test
    void gainingATechnologyChangesThePlan() {
        ResearchPlan before = ResearchPlan.of(test.game, test.sol);

        test.sol.addTech("gd");
        ResearchPlan after = ResearchPlan.of(test.game, test.sol);

        assertThat(after.changedParts(before)).contains("techs");
        assertThat(after.goals().getFirst().needed()).isEqualTo(3);
    }

    @Test
    void gainingASpecialtyPlanetChangesThePlan() {
        ResearchPlan before = ResearchPlan.of(test.game, test.sol);

        test.place("22", "101");
        test.sol.addPlanet("tarmann");
        ResearchPlan after = ResearchPlan.of(test.game, test.sol);

        assertThat(after.changedParts(before)).containsExactly("specialty planets");
    }

    @Test
    void revealingAnObjectiveChangesThePlan() {
        ResearchPlan before = ResearchPlan.of(test.game, test.sol);

        test.game.getRevealedPublicObjectives().put("diversify", 1);
        ResearchPlan after = ResearchPlan.of(test.game, test.sol);

        assertThat(after.changedParts(before)).containsExactly("public objectives");
        assertThat(after.goals()).hasSize(2);
    }

    @Test
    void aBreakthroughSynergyChangesThePlan() {
        ResearchPlan before = ResearchPlan.of(test.game, test.sol);

        test.sol.addBreakthrough("solbt");
        test.sol.setBreakthroughUnlocked("solbt", true);
        ResearchPlan after = ResearchPlan.of(test.game, test.sol);

        assertThat(after.changedParts(before)).contains("synergies");
    }

    @Test
    void aNewRoundChangesThePlan() {
        ResearchPlan before = ResearchPlan.of(test.game, test.sol);

        test.game.setRound(3);
        ResearchPlan after = ResearchPlan.of(test.game, test.sol);

        assertThat(after.changedParts(before)).containsExactly("round");
        assertThat(after.round()).isEqualTo(3);
    }

    @Test
    void twoGamesOnTwoThreadsKeepTheirOwnPlans() throws Exception {
        AiTestGame other = new AiTestGame();
        other.game.setName("other-test-game");
        other.sol.setSecret("mlp");
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<List<String>> mine = pool.submit(plans(test));
            Future<List<String>> theirs = pool.submit(plans(other));

            assertThat(mine.get()).hasSize(50).containsOnly(mine.get().getFirst());
            assertThat(theirs.get()).hasSize(50).containsOnly(theirs.get().getFirst());
            assertThat(mine.get().getFirst()).contains("ans").doesNotContain("mlp");
            assertThat(theirs.get().getFirst()).contains("mlp").doesNotContain("ans (");
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void nekroCannotResearchSoItsPlanIsEmpty() {
        test.nekro.setSecret("ans");

        ResearchPlan plan = ResearchPlan.of(test.game, test.nekro);

        assertThat(plan.steps()).isEmpty();
        assertThat(plan.goals()).isEmpty();
        assertThat(TechPath.route(test.game, test.nekro, "ans")).isEmpty();
    }

    @Test
    void twilightsFallGetsAnEmptyPlan() {
        test.game.setTwilightsFallMode(true);

        ResearchPlan plan = ResearchPlan.of(test.game, test.sol);

        assertThat(plan.steps()).isEmpty();
        assertThat(plan.goals()).isEmpty();
    }

    // Asteroid fields stop ships without Antimass Deflectors, so when the only way to Mecatol runs through one, the
    // technology is on the plan with the reason; when there is another way round it is not.
    @Test
    void antimassDeflectorsArePlannedOnlyWhenAnAsteroidFieldBlocksTheWay() {
        test.sol.removeTech("amd");
        String asteroids = asteroidSystem();
        test.place("01", AiTestGame.HOME);
        String middle = AiTestGame.neighbourOf(AiTestGame.HOME);
        String beyond = MapPositionMapper.getAdjacentTilePositions(middle).stream()
                .filter(position -> !"x".equals(position) && !AiTestGame.HOME.equals(position))
                .filter(position -> !MapPositionMapper.getAdjacentTilePositions(AiTestGame.HOME)
                        .contains(position))
                .findFirst()
                .orElseThrow();
        test.place("18", beyond);
        Tile field = test.place(asteroids, middle);
        assertThat(MovementGraph.reach(test.game, test.sol, AiTestGame.HOME, 4)).doesNotContainKey(beyond);

        ResearchPlan blocked = ResearchPlan.of(test.game, test.sol);

        assertThat(blocked.steps()).anySatisfy(step -> {
            assertThat(step.alias()).isEqualTo("amd");
            assertThat(step.reasons()).anyMatch(reason -> reason.startsWith("reach: asteroids block"));
            assertThat(step.valued()).isTrue();
        });

        test.game.removeTile(field.getPosition());
        test.place("26", middle);
        ResearchPlan.forgetAll();

        assertThat(ResearchPlan.of(test.game, test.sol).steps())
                .noneMatch(step -> step.reasons().stream().anyMatch(reason -> reason.startsWith("reach: asteroids")));
    }

    // Gravity Drive and Carrier II keep the value they already have, so they are shown with their reach reason but
    // earn no bonus of their own.
    @Test
    void reachTechnologiesAreShownWithoutAPlanBonus() {
        test.sol.setSecret("fwp");
        test.sol.addTech("ps");
        test.sol.addTech("da");
        ResearchPlan.forgetAll();

        ResearchPlan plan = ResearchPlan.of(test.game, test.sol);

        Optional<ResearchPlan.Step> destroyer =
                plan.steps().stream().filter(step -> "dd2".equals(step.alias())).findFirst();
        assertThat(destroyer).isPresent();
        assertThat(destroyer.get().reasons()).contains("reach: barrage secret");
        assertThat(destroyer.get().valued()).isFalse();
        assertThat(plan.bonuses(List.of("dd2"))).isEmpty();
    }

    private java.util.concurrent.Callable<List<String>> plans(AiTestGame table) {
        Callable<List<String>> work = () -> {
            List<String> described = new ArrayList<>();
            for (int i = 0; i < 50; i++) {
                described.add(ResearchPlan.of(table.game, table.sol).describe());
            }
            return described;
        };
        return work;
    }

    private String asteroidSystem() {
        return TileHelper.getAllTileModels().stream()
                .filter(model -> model.isAsteroidField() && model.isEmpty())
                .map(model -> model.getId())
                .sorted()
                .findFirst()
                .orElseThrow();
    }

    private List<String> aliases(TechPath.Route route) {
        return route.techs().stream().map(TechnologyModel::getAlias).toList();
    }

    private List<TechnologyType> colours(TechPath.Route route) {
        return route.techs().stream()
                .flatMap(tech -> tech.getTypes().stream())
                .filter(TechnologyType.mainFour::contains)
                .toList();
    }

    private List<String> researchable() {
        List<String> aliases = new ArrayList<>();
        for (TechnologyType type : TechnologyType.values()) {
            ListTechService.getAllTechOfAType(test.game, type.toString().toLowerCase(), test.sol, false, true)
                    .forEach(tech -> aliases.add(tech.getAlias()));
        }
        return aliases.stream().distinct().sorted().toList();
    }

    private boolean planned(String alias) {
        return ResearchPlan.of(test.game, test.sol).steps().stream()
                .anyMatch(step -> step.alias().equals(alias));
    }
}
