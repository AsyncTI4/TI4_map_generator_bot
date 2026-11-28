package ti4.ai.tactical;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.ai.AiTestGame.NOW;
import static ti4.ai.AiTestGame.pressedId;
import static ti4.ai.AiTestGame.prompt;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.eval.MoveEnablers;
import ti4.ai.eval.MovementCard;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.model.MapPositionMapper;
import ti4.service.actioncard.SabotageService;
import ti4.testUtils.BaseTi4Test;

// The planner's Nav Suite pass: plans that only Nav Suite makes legal, because the target is an asteroid field the
// fleet must enter (Thunder's Edge Industrex, tile 115) or the only path crosses a nebula. The card is kept when the
// same plan works without it, and a Sabotaged Nav Suite takes the plan back to legal moves.
class NavSuitePlanTest extends BaseTi4Test {

    private static final String INDUSTREX_SYSTEM = "115";

    private AiTestGame test;
    private Tile home;
    private String neighbour;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.nekro.setTacticalCC(3);
        home = test.nekroHome();
        neighbour = AiTestGame.neighbourOf(AiTestGame.HOME);
        test.units(home, "space", test.nekro, UnitType.Carrier, 1);
        test.units(home, "mordaiii", test.nekro, UnitType.Infantry, 3);
    }

    @Test
    void anAsteroidFieldPlanetIsClaimedOnlyWithNavSuite() {
        test.place(INDUSTREX_SYSTEM, neighbour);
        assertThat(planFor(neighbour)).isEmpty();

        test.nekro.setActionCard("nav_suite", 403);
        Optional<TacticalPlan> plan = planFor(neighbour);

        assertThat(plan).isPresent();
        assertThat(plan.get().landings()).containsKey("industrex");
        assertThat(TacticalPlanner.needsCard(test.game, test.nekro, plan.get(), MovementCard.NAV_SUITE))
                .isTrue();
        assertThat(TacticalPlanner.nextMovementCard(test.game, test.nekro, plan.get()))
                .contains(MovementCard.NAV_SUITE);
    }

    // An invasion of a planet the enemy holds inside an asteroid field is a strike only Nav Suite opens.
    @Test
    void navSuiteOpensAStrikeIntoAnAsteroidField() {
        Tile industrex = test.place(INDUSTREX_SYSTEM, neighbour);
        test.sol.addPlanet("industrex");
        test.units(industrex, "industrex", test.sol, UnitType.Infantry, 1);
        test.units(home, "space", test.nekro, UnitType.Dreadnought, 2);
        assertThat(planFor(neighbour)).isEmpty();

        test.nekro.setActionCard("nav_suite", 403);
        Optional<TacticalPlan> plan = planFor(neighbour);

        assertThat(plan).isPresent();
        assertThat(plan.get().landings()).containsKey("industrex");
        assertThat(TacticalPlanner.nextMovementCard(test.game, test.nekro, plan.get()))
                .contains(MovementCard.NAV_SUITE);
    }

    // The only way to the system two steps out runs through a nebula, which stops movement (LRR 59.1a) unless Nav
    // Suite lets the carrier through (Nav Suite V).
    @Test
    void navSuiteCrossesANebulaToTheOnlyPath() {
        test.nekro.addTech("gd");
        test.place("42", neighbour);
        String far = twoSystemsFrom(neighbour);
        test.place("27", far);
        assertThat(planFor(far)).isEmpty();

        test.nekro.setActionCard("nav_suite", 403);
        Optional<TacticalPlan> plan = planFor(far);

        assertThat(plan).isPresent();
        assertThat(TacticalPlanner.nextMovementCard(test.game, test.nekro, plan.get()))
                .contains(MovementCard.NAV_SUITE);
    }

    @Test
    void navSuiteIsKeptWhenItOpensNothing() {
        test.place("26", neighbour);
        test.nekro.setActionCard("nav_suite", 403);

        Optional<TacticalPlan> plan = planFor(neighbour);

        assertThat(plan).isPresent();
        assertThat(TacticalPlanner.nextMovementCard(test.game, test.nekro, plan.get()))
                .isEmpty();
        assertThat(TacticalPlanner.ranked(test.game, test.nekro))
                .noneMatch(found -> TacticalPlanner.needsCard(test.game, test.nekro, found, MovementCard.NAV_SUITE));
        assertThat(MovementCardGates.worthAPass(test.game, test.nekro, MovementCard.NAV_SUITE, List.of(home)))
                .isFalse();
    }

    // The AI moves its planned ships once Nav Suite resolved. If the card is Sabotaged its stored effect is cleared,
    // the plan no longer reaches, and the AI drops the illegal moves and finishes movement instead of moving them.
    @Test
    void aSabotagedNavSuiteTakesThePlanBackToLegalMoves() {
        test.place(INDUSTREX_SYSTEM, neighbour);
        test.nekro.setActionCard("nav_suite", 403);
        TacticalPlan plan = planFor(neighbour).orElseThrow();
        test.aiIsActive("action");
        test.game.setActiveSystem(neighbour);
        test.game.setStoredValue("currentActionSummary" + test.nekro.getFaction(), " Activated " + neighbour);
        TacticalRules.remember(test.context(), plan);
        test.nekro.getActionCards().remove("nav_suite");
        test.game.setStoredValue(MoveEnablers.NAV_SUITE_PLAYED, test.nekro.getFaction());
        AiPrompt movement = prompt(
                "move",
                PromptSource.PUBLIC,
                NOW,
                "FFCC_nekro_tacticalMoveFrom_" + AiTestGame.HOME,
                "FFCC_nekro_concludeMove_" + neighbour);

        assertThat(pressedId(
                        TacticalRules.continueAction(test.context(movement)).orElseThrow()))
                .isEqualTo("FFCC_nekro_tacticalMoveFrom_" + AiTestGame.HOME);

        SabotageService.undoEffectsOfCanceledCard(test.game, test.nekro, "Nav Suite");

        assertThat(pressedId(
                        TacticalRules.continueAction(test.context(movement)).orElseThrow()))
                .isEqualTo("FFCC_nekro_concludeMove_" + neighbour);
        assertThat(TacticalRules.rememberedPlan(test.context()).orElseThrow().moves())
                .isEmpty();
    }

    private Optional<TacticalPlan> planFor(String position) {
        return TacticalPlanner.ranked(test.game, test.nekro).stream()
                .filter(plan -> plan.target().equals(position))
                .findFirst();
    }

    private static String twoSystemsFrom(String neighbour) {
        List<String> nearHome = MapPositionMapper.getAdjacentTilePositions(AiTestGame.HOME);
        return MapPositionMapper.getAdjacentTilePositions(neighbour).stream()
                .filter(position ->
                        !"x".equals(position) && !AiTestGame.HOME.equals(position) && !nearHome.contains(position))
                .findFirst()
                .orElseThrow();
    }
}
