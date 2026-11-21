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
        return gainPrompt(content, NOW);
    }

    private AiPrompt gainPrompt(String content, long created) {
        List<String> ids = new ArrayList<>(GAIN_BUTTONS);
        ids.add("FFCC_nekro_deleteButtons");
        List<String> labels = List.of("Gain Tactic", "Gain Fleet", "Gain Strategy", "Done Gaining Command Tokens");
        return AiTestGame.withContent(AiTestGame.prompt("gain", PromptSource.PUBLIC, created, ids, labels), content);
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

    // Outside the Technology strategy card, Get a Technology first asks which type of technology to get, then lists
    // that type; the bot answers the type question before picking from the list.
    @Test
    void picksATechnologyTypeWhenAskedBeforeTheList() {
        AiPrompt message = research(sol.getRepresentation() + TECHNOLOGY_MESSAGE, NOW);
        assertThat(pressed(RiderPayouts.technology(context(sol, NOW, Set.of(), message))))
                .isEqualTo("acquireATech");

        AiPrompt types = AiTestGame.prompt(
                "types",
                PromptSource.AI_THREAD,
                NOW,
                "FFCC_sol_getAllTechOfType_propulsion",
                "FFCC_sol_getAllTechOfType_biotic",
                "FFCC_sol_getAllTechOfType_cybernetic",
                "FFCC_sol_getAllTechOfType_warfare");
        assertThat(pressed(StrategyCardRules.chooseTechnology(context(sol, NOW, Set.of(), types))))
                .startsWith("FFCC_sol_getAllTechOfType_");

        AiPrompt options = AiTestGame.prompt("options", PromptSource.AI_THREAD, NOW, "FFCC_sol_getTech_cl2__noPay");
        assertThat(StrategyCardRules.chooseTechnology(context(sol, NOW, Set.of(), types, options)))
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

    // AgendaHelper posts this line, then the gain buttons, to the winner's channel when a Leadership Rider pays out.
    private AiPrompt announcement(Player winner, long created) {
        return AiTestGame.withContent(
                AiTestGame.prompt("announce", PromptSource.PUBLIC, created),
                winner.getRepresentationUnfogged()
                        + " resolve _Leadership Rider_ by using the button to gain 3 command tokens.");
    }

    private String announcedGainPrompt() {
        return nekro.getRepresentation()
                + ", your current command tokens are 3/3/2. Use buttons to gain command tokens.";
    }

    private String gain(AiPrompt... prompts) {
        return pressed(RiderPayouts.leadership(context(nekro, NOW, Set.of(), prompts)));
    }

    // The bot rewrites the prompt's text after each token, so the AI has to remember the prompt to collect all three
    // and then press Done.
    @Test
    void gainsThreeTokensWithAWinningLeadershipRider() {
        AiPrompt announced = announcement(nekro, NOW);
        assertThat(gain(announced, gainPrompt(announcedGainPrompt()))).startsWith("FFCC_nekro_increase_");

        nekro.setStrategicCC(3);
        AiPrompt rewritten = gainPrompt(nekro.getRepresentation() + " command tokens have gone from 3/3/2 -> 3/3/3.");
        assertThat(gain(announced, rewritten)).startsWith("FFCC_nekro_increase_");

        nekro.setTacticalCC(4);
        nekro.setStrategicCC(4);
        assertThat(gain(announced, rewritten)).isEqualTo("FFCC_nekro_deleteButtons");
    }

    // Live game aiplay1: Miscount Disclosed reveals the revote agenda in the same press that pays the riders, which
    // clears the resolved outcome and the votes before the AI looks. The payout line alone proves the win.
    @Test
    void gainsAfterMiscountDisclosedClearedTheResolvedOutcome() {
        test.game.setStoredValue("resolvedAgendaOutcome", "");

        assertThat(gain(announcement(nekro, NOW), gainPrompt(announcedGainPrompt())))
                .startsWith("FFCC_nekro_increase_");
    }

    // Another prompt (here Leadership's secondary, resolved later in aiplay1) overwrote the stored starting tokens with
    // a lower count, so the bot's net gain already reads 3. The AI counts from the tokens it held when it first saw
    // the rider buttons instead.
    @Test
    void countsFromItsOwnStartWhenTheStoredStartingTokensAreStale() {
        test.game.setStoredValue("originalCCsFornekro", "2/2/1");
        AiPrompt announced = announcement(nekro, NOW);
        AiPrompt buttons = gainPrompt(announcedGainPrompt());

        assertThat(gain(announced, buttons)).startsWith("FFCC_nekro_increase_");
        nekro.setTacticalCC(5);
        nekro.setStrategicCC(3);
        assertThat(gain(announced, buttons)).isEqualTo("FFCC_nekro_deleteButtons");
    }

    // An unresolved rider stays owed: the AI resolves it even when it gets to the buttons long after the agenda.
    @Test
    void resolvesARiderWhoseButtonsAreOld() {
        long old = NOW - 2 * 60 * 60_000L;

        assertThat(gain(announcement(nekro, old), gainPrompt(announcedGainPrompt(), old)))
                .startsWith("FFCC_nekro_increase_");
    }

    @Test
    void gainsNothingWithoutAPayoutLineForItself() {
        assertThat(gain(gainPrompt(announcedGainPrompt()))).isEmpty();
        assertThat(gain(announcement(sol, NOW), gainPrompt(announcedGainPrompt())))
                .isEmpty();
        assertThat(gain(announcement(nekro, NOW + 1_000L), gainPrompt(announcedGainPrompt())))
                .isEmpty();
    }

    @Test
    void stopsGainingAtTheCommandTokenLimit() {
        nekro.setTacticalCC(8);
        nekro.setFleetCC(5);
        nekro.setStrategicCC(3);
        test.game.setStoredValue("originalCCsFornekro", nekro.getCCRepresentation());
        String announced = nekro.getRepresentation()
                + ", your current command tokens are 8/5/3. Use buttons to gain command tokens.";

        assertThat(gain(announcement(nekro, NOW), gainPrompt(announced))).isEqualTo("FFCC_nekro_deleteButtons");
    }

    @Test
    void leavesTheGainButtonsOfOtherMessagesAlone() {
        assertThat(gain(
                        announcement(nekro, NOW),
                        gainPrompt(nekro.getRepresentation() + " you have been hit by a mech.")))
                .isEmpty();
    }
}
