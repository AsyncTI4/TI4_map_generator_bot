package ti4.ai.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.ai.AiTestGame.NOW;
import static ti4.ai.AiTestGame.prompt;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.brain.AiDecision;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

class WarfareTokensTest extends BaseTi4Test {

    private static final int WARFARE = 6;
    private static final String PREFIX = "FFCC_nekro_";

    private AiTestGame test;
    private AiPrompt card;
    private AiPrompt primary;
    private AiPrompt tokens;

    @BeforeEach
    void setUp() {
        test = AiTestGame.withSolAi();
        test.aiIsActive("action");
        Tile home = test.nekroHome();
        home.addCC(ti4.model.Mapper.getCCID(test.nekro.getColor()));
        test.nekro.setTg(10);
    }

    // Raise a Fleet is revealed and four ships sit at home, so the standing split wants five fleet tokens. The fleet
    // pool is not touched mid-round, so the old rule moved a strategy token to tactics, then moved another one back,
    // over and over until the seat ran out of attempts, and the fallback finished the redistribution a token short.
    @Test
    void settlesWithoutLosingATokenWhenTheStandingSplitWantsMoreFleetTokens() {
        test.units(test.nekroHome(), "space", test.nekro, UnitType.Dreadnought, 4);
        test.game.getRevealedPublicObjectives().put("raise_fleet", 1);
        tokens(3, 3, 2);

        Redistribution result = redistribute();

        assertThat(result.repeatedPress()).isNull();
        assertThat(result.finished()).isTrue();
        assertThat(held()).isEqualTo(8);
    }

    // A pool left with a single token moved away used to end the redistribution at once, because the move budget was
    // counted in the tokens held after the move.
    @Test
    void putsTheMovedTokenBackWhenNothingIsLeftToCountTheBudgetWith() {
        test.game.getRevealedPublicObjectives().put("raise_fleet", 1);
        test.game.getRevealedPublicObjectives().put("command_armada", 1);
        tokens(0, 0, 1);

        Redistribution result = redistribute();

        assertThat(result.repeatedPress()).isNull();
        assertThat(result.finished()).isTrue();
        assertThat(held()).isEqualTo(1);
    }

    @Test
    void neverEndsWithFewerTokensThanItStartedWith() {
        Random random = new Random(7);
        for (int i = 0; i < 300; i++) {
            setUp();
            int ships = random.nextInt(7);
            if (ships > 0) test.units(test.nekroHome(), "space", test.nekro, UnitType.Dreadnought, ships);
            if (random.nextBoolean()) test.game.getRevealedPublicObjectives().put("raise_fleet", 1);
            if (random.nextBoolean()) test.game.getRevealedPublicObjectives().put("command_armada", 1);
            if (random.nextInt(4) == 0) test.game.addLaw("regulations", null);
            tokens(random.nextInt(6), random.nextInt(6), random.nextInt(5));
            int started = held();
            String setup = "ships " + ships + ", objectives "
                    + test.game.getRevealedPublicObjectives().keySet() + ", tokens " + test.nekro.getCCRepresentation();

            Redistribution result = redistribute();

            assertThat(result.repeatedPress()).as(setup).isNull();
            assertThat(result.finished()).as(setup).isTrue();
            assertThat(held()).as(setup).isEqualTo(started);
        }
    }

    private record Redistribution(boolean finished, String repeatedPress) {}

    private Redistribution redistribute() {
        card = prompt("warfare", PromptSource.PUBLIC, NOW, "sc_no_follow_6", "primaryOfTeWarfare");
        StrategyCardRules.play(test.context(card), card, card.buttons().getFirst(), WARFARE);
        StrategyCardRules.resolvePrimary(test.context(card));
        primary = prompt(
                "primary",
                PromptSource.PUBLIC,
                NOW,
                PREFIX + "redistributeCCButtons_deleteThisButton",
                PREFIX + "beginTacticalTeWarfare");
        Optional<AiDecision> opening = StrategyCardRules.resolvePrimary(test.context(card, primary));
        if (opening.isEmpty() || !AiTestGame.pressedId(opening.get()).contains("redistributeCCButtons")) {
            return new Redistribution(true, null);
        }
        test.game.setStoredValue("originalCCsFornekro", test.nekro.getCCRepresentation());
        tokens = prompt(
                "tokens",
                PromptSource.AI_THREAD,
                NOW + 10,
                List.of(
                        PREFIX + "increase_tactic_cc",
                        PREFIX + "increase_fleet_cc",
                        PREFIX + "increase_strategy_cc",
                        PREFIX + "decrease_tactic_cc",
                        PREFIX + "decrease_fleet_cc",
                        PREFIX + "decrease_strategy_cc",
                        PREFIX + "deleteButtons"),
                List.of(
                        "Gain 1 Tactic Token",
                        "Gain 1 Fleet Token",
                        "Gain 1 Strategy Token",
                        "Lose 1 Tactic Token",
                        "Lose 1 Fleet Token",
                        "Lose 1 Strategy Token",
                        "Done Redistributing Command Tokens"));
        return drive();
    }

    // The runtime lets a seat press the same button in the same state twice; a third time is "no progress".
    private Redistribution drive() {
        Map<String, Integer> pressesInState = new HashMap<>();
        for (int step = 0; step < 60; step++) {
            Optional<AiDecision> decision = StrategyCardRules.resolvePrimary(test.context(card, primary, tokens));
            if (decision.isEmpty() || !(decision.get() instanceof AiDecision.Press press)) {
                return new Redistribution(false, null);
            }
            String id = press.button().customId().substring(PREFIX.length());
            if ("deleteButtons".equals(id)) return new Redistribution(true, null);
            String inState = id + " at " + test.nekro.getCCRepresentation();
            if (pressesInState.merge(inState, 1, Integer::sum) > 2) return new Redistribution(false, inState);
            apply(id);
        }
        return new Redistribution(false, "no end in sight");
    }

    private void apply(String handler) {
        int delta = handler.startsWith("increase") ? 1 : -1;
        if (handler.contains("tactic")) test.nekro.setTacticalCC(test.nekro.getTacticalCC() + delta);
        if (handler.contains("fleet")) test.nekro.setFleetCC(test.nekro.getFleetCC() + delta);
        if (handler.contains("strategy")) test.nekro.setStrategicCC(test.nekro.getStrategicCC() + delta);
    }

    private void tokens(int tactic, int fleet, int strategy) {
        test.nekro.setTacticalCC(tactic);
        test.nekro.setFleetCC(fleet);
        test.nekro.setStrategicCC(strategy);
    }

    private int held() {
        return test.nekro.getTacticalCC() + test.nekro.getFleetCC() + test.nekro.getStrategicCC();
    }
}
