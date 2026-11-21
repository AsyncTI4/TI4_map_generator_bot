package ti4.ai.winnu;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.ai.AiTestGame.NOW;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.brain.AiDecision;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.ai.tactical.TacticalRules;
import ti4.testUtils.BaseTi4Test;

// Paying for a build as the Winnu. The bot's payment prompt offers the planets, Berekar Berekon (owned by the seat: the
// agent cuts the cost by 2) and Hegemonic Trade Policy (unowned: the bot only exhausts it and lists it as spent, so the
// AI itself counts the planet it swaps at its influence). Here the build costs 5; the seat holds Winnu (3/4) and
// Mecatol Rex (1/6).
class WinnuProductionTest extends BaseTi4Test {

    private static final String SOURCE = "tacticalAction";
    private static final String AGENT = "FFCC_winnu_exhaustAgent_winnuagent";
    private static final String HTP = "exhaustTech_htp";
    private static final String MECATOL = "spend_mr_res";
    private static final String HOME = "spend_winnu_res";
    private static final String DONE = "deleteButtons_" + SOURCE;

    private WinnuTestBoard board;

    @BeforeEach
    void setUp() {
        board = new WinnuTestBoard();
        board.winnuActive("action");
        board.test.place("07", "201");
        board.winnu.addPlanet("winnu");
        board.holdMecatol();
        board.winnu.addTech(WinnuProduction.HEGEMONIC_TRADE_POLICY);
        board.test.game.setStoredValue("producedUnitCostForwinnu", "5");
    }

    @Test
    void theAgentTakesTwoOffABuildOfTwoOrMore() {
        assertThat(pay(AGENT, HTP, MECATOL, HOME)).contains(AGENT);
    }

    @Test
    void theAgentIsKeptForABuildUnderTwo() {
        board.test.game.setStoredValue("producedUnitCostForwinnu", "1");

        assertThat(pay(AGENT, MECATOL, HOME)).isPresent().get().isNotEqualTo(AGENT);
    }

    // With 3 still owed, Mecatol Rex spent for its 6 influence covers it all: the policy is exhausted first.
    @Test
    void swapsMecatolRexWhenItsInfluenceIsNeeded() {
        board.winnu.addSpentThing(WinnuProduction.AGENT);

        assertThat(pay(HTP, MECATOL, HOME)).contains(HTP);
    }

    @Test
    void thenSpendsTheSwappedPlanet() {
        board.winnu.addSpentThing(WinnuProduction.AGENT);
        board.winnu.addSpentThing(WinnuProduction.HEGEMONIC_TRADE_POLICY);

        assertThat(pay(MECATOL, HOME)).contains(MECATOL);
    }

    // The agent's 2 and the swapped Mecatol Rex's 6 cover the 5: nothing else is exhausted.
    @Test
    void countsTheSwappedPlanetAtItsInfluence() {
        board.winnu.addSpentThing(WinnuProduction.AGENT);
        board.winnu.addSpentThing(WinnuProduction.HEGEMONIC_TRADE_POLICY);
        board.winnu.addSpentThing(WinnuTestBoard.MECATOL);
        board.winnu.exhaustPlanet(WinnuTestBoard.MECATOL);

        assertThat(pay(HOME)).contains(DONE);
    }

    // A swap that gains less than 2 is not worth the exhaust: Winnu (3/4) is just spent.
    @Test
    void keepsThePolicyForASmallSwap() {
        board.winnu.addSpentThing(WinnuProduction.AGENT);
        board.winnu.exhaustPlanet(WinnuTestBoard.MECATOL);

        assertThat(pay(HTP, HOME)).contains(HOME);
    }

    private Optional<String> pay(String... offered) {
        List<String> ids = new java.util.ArrayList<>(List.of(offered));
        ids.add(DONE);
        List<String> labels = ids.stream()
                .map(id -> DONE.equals(id) ? "Done Exhausting Planets" : id)
                .toList();
        AiPrompt payment = AiTestGame.prompt("payment", PromptSource.PUBLIC, NOW, ids, labels);
        Optional<AiDecision> decision = TacticalRules.payForUnits(
                board.test.contextFor(board.winnu, Set.of(), NOW, payment), List.of(payment), SOURCE);
        return decision.map(AiTestGame::pressedId);
    }
}
