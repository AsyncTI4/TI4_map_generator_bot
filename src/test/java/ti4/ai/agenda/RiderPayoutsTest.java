package ti4.ai.agenda;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.ai.AiTestGame.NOW;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.ai.strategy.StrategyCardRules;
import ti4.game.Player;
import ti4.testUtils.BaseTi4Test;

class RiderPayoutsTest extends BaseTi4Test {

    private static final List<String> GAIN_BUTTONS =
            List.of("FFCC_nekro_increase_tactic_cc", "FFCC_nekro_increase_fleet_cc", "FFCC_nekro_increase_strategy_cc");
    private static final String TECHNOLOGY_MESSAGE =
            ", please resolve _Technology Rider_ by using the button to research 1 technology.";

    private AiTestGame test;
    private Player sol;
    private Player nekro;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        sol = test.sol;
        nekro = test.nekro;
        nekro.setTacticalCC(3);
        nekro.setFleetCC(3);
        nekro.setStrategicCC(2);
        test.game.setStoredValue("originalCCsFornekro", nekro.getCCRepresentation());
    }

    private AiTurnContext context(Player seat, long now, Set<String> pressed, AiPrompt... prompts) {
        return test.contextFor(seat, pressed, now, prompts);
    }

    private AiPrompt research(String content, long created) {
        return AiTestGame.withContent(
                AiTestGame.prompt("research", PromptSource.PUBLIC, created, "acquireATech"), content);
    }

    private AiPrompt gainPrompt(String content) {
        List<String> ids = new ArrayList<>(GAIN_BUTTONS);
        ids.add("FFCC_nekro_deleteButtons");
        List<String> labels = List.of("Gain Tactic", "Gain Fleet", "Gain Strategy", "Done Gaining Command Tokens");
        return AiTestGame.withContent(AiTestGame.prompt("gain", PromptSource.PUBLIC, NOW, ids, labels), content);
    }

    private static String pressed(Optional<AiDecision> decision) {
        return decision.map(AiTestGame::pressedId).orElse("");
    }

    // ---- Technology Rider ----------------------------------------------------------------------------------------

    @Test
    void researchesWithAWinningTechnologyRiderAndChoosesAtNoCost() {
        AiPrompt message = research(sol.getRepresentation() + TECHNOLOGY_MESSAGE, NOW);

        assertThat(pressed(RiderPayouts.technology(context(sol, NOW, Set.of(), message))))
                .isEqualTo("acquireATech");

        AiPrompt options = AiTestGame.prompt("options", PromptSource.AI_THREAD, NOW, "FFCC_sol_getTech_cl2__noPay");
        assertThat(StrategyCardRules.chooseTechnology(context(sol, NOW, Set.of(), options)))
                .isPresent();
    }

    @Test
    void leavesAGetATechnologyButtonThatIsNotForItsRider() {
        AiPrompt focusedResearch =
                research(sol.getRepresentation() + ", please use the button to research a technology.", NOW);

        assertThat(RiderPayouts.technology(context(sol, NOW, Set.of(), focusedResearch)))
                .isEmpty();
        assertThat(RiderPayouts.technology(context(sol, NOW, Set.of(), research(TECHNOLOGY_MESSAGE, NOW))))
                .isEmpty();
    }

    @Test
    void leavesAnotherPlayersTechnologyRiderAndOldMessagesAlone() {
        AiPrompt theirs = research(nekro.getRepresentation() + TECHNOLOGY_MESSAGE, NOW);
        assertThat(RiderPayouts.technology(context(sol, NOW, Set.of(), theirs))).isEmpty();

        AiPrompt old = research(sol.getRepresentation() + TECHNOLOGY_MESSAGE, NOW - 31 * 60_000L);
        assertThat(RiderPayouts.technology(context(sol, NOW, Set.of(), old))).isEmpty();
    }

    @Test
    void pressesTheTechnologyRiderButtonOnlyOnce() {
        AiPrompt message = research(sol.getRepresentation() + TECHNOLOGY_MESSAGE, NOW);
        Set<String> done =
                Set.of(AiTurnContext.pressKey(message, message.buttons().getFirst()));

        assertThat(RiderPayouts.technology(context(sol, NOW, done, message))).isEmpty();
    }

    // Propagation turns the research into command tokens, which the Propagation rule collects.
    @Test
    void leavesTheTechnologyRiderOfASeatWithPropagationToThatRule() {
        AiPrompt message = research(nekro.getRepresentation() + TECHNOLOGY_MESSAGE, NOW);

        assertThat(RiderPayouts.technology(context(nekro, NOW, Set.of(), message)))
                .isEmpty();
    }

    // ---- Leadership Rider ----------------------------------------------------------------------------------------

    private void leadershipRiderWon() {
        test.game.setStoredValue("resolvedAgendaOutcome", "for");
        test.game.setCurrentAgendaVote("for", "nekro_Leadership Rider");
    }

    private String gain(AiPrompt prompt) {
        return pressed(RiderPayouts.leadership(context(nekro, NOW, Set.of(), prompt)));
    }

    // The bot rewrites the prompt's text after each token, so the AI has to remember the prompt to collect all three
    // and then press Done.
    @Test
    void gainsThreeTokensWithAWinningLeadershipRider() {
        leadershipRiderWon();
        String announced = nekro.getRepresentation()
                + ", your current command tokens are 3/3/2. Use buttons to gain command tokens.";
        assertThat(gain(gainPrompt(announced))).startsWith("FFCC_nekro_increase_");

        nekro.setStrategicCC(3);
        AiPrompt rewritten = gainPrompt(nekro.getRepresentation() + " command tokens have gone from 3/3/2 -> 3/3/3.");
        assertThat(gain(rewritten)).startsWith("FFCC_nekro_increase_");

        nekro.setTacticalCC(4);
        nekro.setStrategicCC(4);
        assertThat(gain(rewritten)).isEqualTo("FFCC_nekro_deleteButtons");
    }

    @Test
    void gainsNothingWithoutAWinningLeadershipRider() {
        String announced = nekro.getRepresentation()
                + ", your current command tokens are 3/3/2. Use buttons to gain command tokens.";
        assertThat(gain(gainPrompt(announced))).isEmpty();

        test.game.setStoredValue("resolvedAgendaOutcome", "for");
        test.game.setCurrentAgendaVote("against", "nekro_Leadership Rider");
        assertThat(gain(gainPrompt(announced))).isEmpty();
    }

    @Test
    void stopsGainingAtTheCommandTokenLimit() {
        leadershipRiderWon();
        nekro.setTacticalCC(8);
        nekro.setFleetCC(5);
        nekro.setStrategicCC(3);
        test.game.setStoredValue("originalCCsFornekro", nekro.getCCRepresentation());
        String announced = nekro.getRepresentation()
                + ", your current command tokens are 8/5/3. Use buttons to gain command tokens.";

        assertThat(gain(gainPrompt(announced))).isEqualTo("FFCC_nekro_deleteButtons");
    }

    @Test
    void leavesTheGainButtonsOfOtherMessagesAlone() {
        leadershipRiderWon();

        assertThat(gain(gainPrompt(nekro.getRepresentation() + " you have been hit by a mech.")))
                .isEmpty();
    }
}
