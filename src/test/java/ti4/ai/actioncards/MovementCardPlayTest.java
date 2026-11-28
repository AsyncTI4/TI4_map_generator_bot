package ti4.ai.actioncards;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static ti4.ai.AiTestGame.NOW;
import static ti4.ai.AiTestGame.prompt;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import ti4.ai.AiTestGame;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.eval.Departure;
import ti4.ai.eval.MoveBudget;
import ti4.ai.eval.MoveEnablers;
import ti4.ai.eval.MovementCard;
import ti4.ai.eval.ShipSpeed;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.ai.tactical.TacticalPlan;
import ti4.ai.tactical.TacticalPlan.UnitMove;
import ti4.ai.tactical.TacticalPlanner;
import ti4.ai.tactical.TacticalRules;
import ti4.game.Tile;
import ti4.helpers.ButtonHelperTacticalAction;
import ti4.helpers.Units.UnitType;
import ti4.message.GameMessage;
import ti4.message.GameMessageManager;
import ti4.message.GameMessageType;
import ti4.model.MapPositionMapper;
import ti4.service.actioncard.SabotageService;
import ti4.testUtils.BaseTi4Test;

// After activating a system the AI plays Flank Speed, Lost Star Chart or Nav Suite only when its remembered plan
// cannot be moved without the card. Each card has its own play memory, so two can be played in one tactical action.
class MovementCardPlayTest extends BaseTi4Test {

    private AiTestGame test;
    private MockedStatic<GameMessageManager> messages;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.nekroHome();
        test.nekro.setTacticalCC(3);
        messages = Mockito.mockStatic(GameMessageManager.class);
        messages.when(() -> GameMessageManager.getOne(anyString(), anyString()))
                .thenAnswer(call -> Optional.of(new GameMessage(
                        call.getArgument(1), GameMessageType.ACTION_CARD, new LinkedHashSet<>(List.of("nekro")), 0L)));
    }

    @AfterEach
    void tearDown() {
        messages.close();
    }

    private void activate(String target) {
        test.aiIsActive("action");
        test.game.setActiveSystem(target);
        test.game.setStoredValue("currentActionSummary" + test.nekro.getFaction(), " Activated " + target);
    }

    private TacticalPlan plan(String target, UnitType type) {
        return new TacticalPlan(
                TacticalPlan.Kind.POSITION,
                target,
                List.of(new UnitMove(AiTestGame.HOME, "space", type, 1)),
                Map.of(),
                3.0);
    }

    private AiTurnContext contextWith(TacticalPlan plan, AiPrompt... prompts) {
        AiTurnContext context = test.context(prompts);
        TacticalRules.remember(context, plan);
        return context;
    }

    private static AiPrompt hand(String... ids) {
        return prompt("hand", PromptSource.AI_THREAD, NOW, ids);
    }

    private static String pressed(Optional<AiDecision> decision) {
        return decision.map(AiTestGame::pressedId).orElse("");
    }

    private boolean reaches(String target) {
        Tile tile = test.game.getTileByPosition(target);
        return MoveBudget.feasible(
                test.game, test.nekro, tile, List.of(new Departure(AiTestGame.HOME, UnitType.Carrier, 1)));
    }

    // A carrier (move 1) next to an alpha wormhole: Lost Star Chart takes it to the beta system on the far side of the
    // board, one move further than it can go, so it needs Flank Speed as well. Flank Speed is played first, then the
    // chart once the boost is in effect.
    @Test
    void flankSpeedAndLostStarChartBothPlayInOneTurn() {
        String alpha = AiTestGame.neighbourOf(AiTestGame.HOME);
        test.place("26", alpha);
        String beta = farFrom(AiTestGame.HOME, alpha);
        test.place("25", beta);
        test.units(test.game.getTileByPosition(AiTestGame.HOME), "space", test.nekro, UnitType.Carrier, 1);
        test.nekro.setActionCard("fs1", 401);
        test.nekro.setActionCard("lost_star", 402);
        activate(beta);
        TacticalPlan toBeta = plan(beta, UnitType.Carrier);
        AiPrompt cards = hand("ac_play_from_hand_401", "ac_play_from_hand_402");

        assertThat(pressed(ActionCardRules.movementCard(contextWith(toBeta, cards))))
                .isEqualTo("ac_play_from_hand_401");
        assertThat(ActionCardRules.movementCardPending(test.context())).isTrue();

        test.nekro.getActionCards().remove("fs1");
        test.game.setStoredValue(ShipSpeed.FLANK_SPEED_BOOST, "1");
        assertThat(pressed(ActionCardRules.movementCard(contextWith(toBeta, cards))))
                .isEqualTo("ac_play_from_hand_402");

        test.nekro.getActionCards().remove("lost_star");
        test.game.setStoredValue(MoveEnablers.LOST_STAR_CHART_PLAYED, test.nekro.getFaction());
        assertThat(reaches(beta)).isTrue();
        assertThat(ActionCardRules.movementCard(contextWith(toBeta, cards))).isEmpty();
    }

    // Nav Suite lets ships enter an asteroid field without Antimass Deflectors; a plan into a plain system keeps it.
    @Test
    void playsNavSuiteOnlyWhenThePlanNeedsIt() {
        List<String> beside = MapPositionMapper.getAdjacentTilePositions(AiTestGame.HOME).stream()
                .filter(position -> !"x".equals(position))
                .toList();
        String asteroids = beside.get(0);
        String plain = beside.get(1);
        test.place("44", asteroids);
        test.place("19", plain);
        test.units(test.game.getTileByPosition(AiTestGame.HOME), "space", test.nekro, UnitType.Cruiser, 1);
        test.nekro.setActionCard("nav_suite", 403);
        AiPrompt cards = hand("ac_play_from_hand_403");

        activate(plain);
        assertThat(ActionCardRules.movementCard(contextWith(plan(plain, UnitType.Cruiser), cards)))
                .isEmpty();

        activate(asteroids);
        assertThat(pressed(ActionCardRules.movementCard(contextWith(plan(asteroids, UnitType.Cruiser), cards))))
                .isEqualTo("ac_play_from_hand_403");
    }

    // A strike two systems out whose only path crosses a nebula (Nav Suite V). Nav Suite is played once: not again
    // while its effect is in place, and not again after it is Sabotaged.
    @Test
    void playsNavSuiteOnceForAStrikeAcrossANebula() {
        String nebula = AiTestGame.neighbourOf(AiTestGame.HOME);
        test.place("42", nebula);
        List<String> nearHome = MapPositionMapper.getAdjacentTilePositions(AiTestGame.HOME);
        String far = MapPositionMapper.getAdjacentTilePositions(nebula).stream()
                .filter(position ->
                        !"x".equals(position) && !AiTestGame.HOME.equals(position) && !nearHome.contains(position))
                .findFirst()
                .orElseThrow();
        test.place("19", far);
        test.units(test.game.getTileByPosition(AiTestGame.HOME), "space", test.nekro, UnitType.Cruiser, 1);
        test.nekro.setActionCard("nav_suite", 403);
        activate(far);
        TacticalPlan strike = plan(far, UnitType.Cruiser);
        AiPrompt cards = hand("ac_play_from_hand_403");

        assertThat(pressed(ActionCardRules.movementCard(contextWith(strike, cards))))
                .isEqualTo("ac_play_from_hand_403");

        test.game.setStoredValue(MoveEnablers.NAV_SUITE_PLAYED, test.nekro.getFaction());
        assertThat(ActionCardRules.movementCard(contextWith(strike, cards))).isEmpty();

        SabotageService.undoEffectsOfCanceledCard(test.game, test.nekro, "Nav Suite");
        assertThat(ActionCardRules.movementCard(contextWith(strike, cards))).isEmpty();
    }

    // A cruiser starting in a nebula (move 1) aims two systems out; Nav Suite and Flank Speed would each get it there.
    // Nav Suite is played, the ships move and the engine clears the card's effect when movement ends. The remembered
    // plan then looks out of range again, but the "after you activate a system" window is gone: Flank Speed must not
    // be played during space cannon, combat or invasion.
    @Test
    void noMovementCardIsPlayedOnceMovementIsOver() {
        String strike = nebulaStrike();
        TacticalPlan plan = cruiserFromNebula(strike);
        test.nekro.setActionCard("fs1", 401);
        test.nekro.setActionCard("nav_suite", 403);
        AiPrompt cards = hand("ac_play_from_hand_401", "ac_play_from_hand_403");

        AiTurnContext first = contextWith(plan, cards);
        assertThat(pressed(ActionCardRules.movementCard(first))).isEqualTo("ac_play_from_hand_403");
        settle(first, MovementCard.NAV_SUITE);
        test.nekro.getActionCards().remove("nav_suite");
        test.game.setStoredValue(MoveEnablers.NAV_SUITE_PLAYED, test.nekro.getFaction());

        AiPrompt movement = prompt("move", PromptSource.PUBLIC, NOW, "FFCC_nekro_concludeMove_" + strike);
        assertThat(pressed(TacticalRules.continueAction(test.context(movement))))
                .isEqualTo("FFCC_nekro_concludeMove_" + strike);
        ButtonHelperTacticalAction.clearMovementEnablers(test.game);

        // The premise: with the effect gone, the plan alone would ask for Flank Speed.
        assertThat(TacticalPlanner.nextMovementCard(test.game, test.nekro, plan))
                .contains(MovementCard.FLANK_SPEED);
        assertThat(ActionCardRules.movementCard(contextWith(plan, cards))).isEmpty();
    }

    // The same guard without the AI's own record of pressing "finish moving" (a fallback press, say): a post-movement
    // prompt newer than any movement prompt closes the window too.
    @Test
    void aPostMovementPromptAlsoClosesTheMovementCardWindow() {
        String strike = nebulaStrike();
        TacticalPlan plan = cruiserFromNebula(strike);
        test.nekro.setActionCard("fs1", 401);
        AiPrompt cards = hand("ac_play_from_hand_401");
        AiPrompt moving = prompt("move", PromptSource.PUBLIC, NOW - 1_000L, "FFCC_nekro_concludeMove_" + strike);
        AiPrompt landing = prompt("land", PromptSource.PUBLIC, NOW, "FFCC_nekro_doneLanding_" + strike);

        // Checked first: a play started here would be remembered and hide the guard from the second check.
        assertThat(ActionCardRules.movementCard(contextWith(plan, cards, moving, landing)))
                .isEmpty();
        assertThat(pressed(ActionCardRules.movementCard(contextWith(plan, cards, moving))))
                .isEqualTo("ac_play_from_hand_401");
    }

    // Marks the remembered play of a movement card as resolved, as the play memory does once the card is through.
    private void settle(AiTurnContext context, MovementCard card) {
        String key = PlayMemory.movementKey(context, card);
        CardPlay play = context.memory().get(key).flatMap(CardPlay::decode).orElseThrow();
        context.memory().put(key, play.at(CardPlay.Stage.DONE, NOW).encode());
    }

    // A nebula beside home, a plain system past it and the activated system past that, with a cruiser in the nebula.
    private String nebulaStrike() {
        String nebula = AiTestGame.neighbourOf(AiTestGame.HOME);
        test.place("42", nebula);
        List<String> nearHome = MapPositionMapper.getAdjacentTilePositions(AiTestGame.HOME);
        String middle = MapPositionMapper.getAdjacentTilePositions(nebula).stream()
                .filter(position ->
                        !"x".equals(position) && !AiTestGame.HOME.equals(position) && !nearHome.contains(position))
                .findFirst()
                .orElseThrow();
        test.place("19", middle);
        List<String> nearNebula = MapPositionMapper.getAdjacentTilePositions(nebula);
        String strike = MapPositionMapper.getAdjacentTilePositions(middle).stream()
                .filter(position -> !"x".equals(position)
                        && !AiTestGame.HOME.equals(position)
                        && !nebula.equals(position)
                        && !nearHome.contains(position)
                        && !nearNebula.contains(position))
                .findFirst()
                .orElseThrow();
        test.place("20", strike);
        test.units(test.game.getTileByPosition(nebula), "space", test.nekro, UnitType.Cruiser, 1);
        activate(strike);
        return strike;
    }

    private TacticalPlan cruiserFromNebula(String strike) {
        return new TacticalPlan(
                TacticalPlan.Kind.POSITION,
                strike,
                List.of(new UnitMove(AiTestGame.neighbourOf(AiTestGame.HOME), "space", UnitType.Cruiser, 1)),
                Map.of(),
                3.0);
    }

    // A Sabotaged Lost Star Chart clears its effect: the plan is out of range again, so the tactical rules fall back
    // to legal moves, and the AI does not try to play the same card a second time.
    @Test
    void aSabotagedLostStarChartFallsBackToReachableMoves() {
        String alpha = AiTestGame.neighbourOf(AiTestGame.HOME);
        test.place("26", alpha);
        test.units(test.game.getTileByPosition(alpha), "space", test.nekro, UnitType.Carrier, 1);
        String beta = farFrom(AiTestGame.HOME, alpha);
        test.place("25", beta);
        test.nekro.setActionCard("lost_star", 402);
        activate(beta);
        TacticalPlan toBeta = new TacticalPlan(
                TacticalPlan.Kind.POSITION,
                beta,
                List.of(new UnitMove(alpha, "space", UnitType.Carrier, 1)),
                Map.of(),
                3.0);
        AiPrompt cards = hand("ac_play_from_hand_402");
        assertThat(pressed(ActionCardRules.movementCard(contextWith(toBeta, cards))))
                .isEqualTo("ac_play_from_hand_402");

        test.game.setStoredValue(MoveEnablers.LOST_STAR_CHART_PLAYED, test.nekro.getFaction());
        Tile target = test.game.getTileByPosition(beta);
        List<Departure> carrier = List.of(new Departure(alpha, UnitType.Carrier, 1));
        assertThat(MoveBudget.feasible(test.game, test.nekro, target, carrier)).isTrue();

        SabotageService.undoEffectsOfCanceledCard(test.game, test.nekro, "Lost Star Chart");
        assertThat(MoveBudget.feasible(test.game, test.nekro, target, carrier)).isFalse();
        assertThat(ActionCardRules.movementCard(contextWith(toBeta, cards))).isEmpty();
    }

    // A board position at least three systems from both given positions.
    private static String farFrom(String first, String second) {
        for (int ring = 3; ring >= 1; ring--) {
            for (int index = 1; index <= ring * 6; index++) {
                String candidate = ring + String.format("%02d", index);
                if (candidate.equals(first) || candidate.equals(second)) continue;
                if (withinTwo(first, candidate) || withinTwo(second, candidate)) continue;
                return candidate;
            }
        }
        throw new IllegalStateException("no far position");
    }

    private static boolean withinTwo(String from, String candidate) {
        for (String step : MapPositionMapper.getAdjacentTilePositions(from)) {
            if (step.equals(candidate)) return true;
            if ("x".equals(step)) continue;
            if (MapPositionMapper.getAdjacentTilePositions(step).contains(candidate)) return true;
        }
        return false;
    }
}
