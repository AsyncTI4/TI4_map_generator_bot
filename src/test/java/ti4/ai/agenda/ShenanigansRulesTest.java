package ti4.ai.agenda;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.ai.AiTestGame.NOW;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.nekro.NekroBrain;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.testUtils.BaseTi4Test;

class ShenanigansRulesTest extends BaseTi4Test {

    private static final long LATE = NOW + 30_000L;

    private AiTestGame test;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.game.setPhaseOfGame("agendaEnd");
        test.game.setCurrentAgendaInfo("Law_Elect Player_7_shard_of_the_throne");
    }

    private static AiPrompt prompt(long created, List<String> ids, List<String> labels) {
        return AiTestGame.prompt("shenanigans", PromptSource.PUBLIC, created, ids, labels);
    }

    private static AiPrompt deadlyPlotAndBribery(long created) {
        return prompt(
                created,
                List.of("generic_button_id_2", "generic_button_id_1"),
                List.of("No Bribery", "No Deadly Plot"));
    }

    private static AiPrompt legalText(long created) {
        return prompt(
                created,
                List.of("generic_button_id_3", "genericReact4"),
                List.of("Refuse Confounding Legal Text", "Refuse Confusing Legal Text"));
    }

    private AiTurnContext context(long now, Set<String> pressed, AiPrompt... prompts) {
        return test.contextFor(test.nekro, pressed, now, prompts);
    }

    private static String pressed(Optional<AiDecision> decision) {
        return AiTestGame.pressedId(decision.orElseThrow());
    }

    // Everyone at the table can see that the bot already reacts for a seat holding no shenanigans, so waiting a few
    // seconds before saying "no" gives nothing away. The AI says it for every button, one at a time, each once.
    @Test
    void confirmsEachShenaniganAfterASeededDelayAndOnlyOnce() {
        AiPrompt plots = deadlyPlotAndBribery(NOW);

        assertThat(ShenanigansRules.react(context(NOW, Set.of(), plots))).containsInstanceOf(AiDecision.Wait.class);

        Set<String> done = new HashSet<>();
        Optional<AiDecision> first = ShenanigansRules.react(context(LATE, done, plots));
        assertThat(pressed(first)).startsWith("generic_button_id_");
        done.add(AiTurnContext.pressKey(plots, ((AiDecision.Press) first.get()).button()));

        Optional<AiDecision> second = ShenanigansRules.react(context(LATE, done, plots));
        assertThat(pressed(second)).startsWith("generic_button_id_").isNotEqualTo(pressed(first));
        done.add(AiTurnContext.pressKey(plots, ((AiDecision.Press) second.get()).button()));

        assertThat(ShenanigansRules.react(context(LATE, done, plots))).isEmpty();
    }

    @Test
    void confirmsTheLegalTextsToo() {
        AiPrompt legal = legalText(NOW);
        ShenanigansRules.react(context(NOW, Set.of(), legal));
        Set<String> done = new HashSet<>();
        List<String> pressedIds = new java.util.ArrayList<>();
        for (int press = 0; press < 2; press++) {
            Optional<AiDecision> decision = ShenanigansRules.react(context(LATE, done, legal));
            pressedIds.add(pressed(decision));
            done.add(AiTurnContext.pressKey(legal, ((AiDecision.Press) decision.get()).button()));
        }

        assertThat(pressedIds).containsExactlyInAnyOrder("generic_button_id_3", "genericReact4");
    }

    @Test
    void theDelayDoesNotDependOnTheHand() {
        AiPrompt plots = deadlyPlotAndBribery(NOW);
        Optional<AiDecision> bare = ShenanigansRules.react(context(NOW, Set.of(), plots));

        test.nekro.setActionCard("deadly_plot", 9);
        test.nekro.setActionCard("bribery", 10);
        Optional<AiDecision> holding = ShenanigansRules.react(context(NOW, Set.of(), plots));

        assertThat(((AiDecision.Wait) holding.orElseThrow()).untilMillis())
                .isEqualTo(((AiDecision.Wait) bare.orElseThrow()).untilMillis());
    }

    // Other messages built on the generic buttons (the /button command) are not the AI's to answer.
    @Test
    void leavesOtherGenericButtonsAlone() {
        AiPrompt other = prompt(NOW, List.of("generic_button_id_1"), List.of("Ready for the next round"));

        assertThat(ShenanigansRules.react(context(LATE, Set.of(), other))).isEmpty();
    }

    @Test
    void reactsOnlyAtTheEndOfTheAgenda() {
        test.game.setPhaseOfGame("agendaVoting");

        assertThat(ShenanigansRules.react(context(LATE, Set.of(), deadlyPlotAndBribery(NOW))))
                .isEmpty();
    }

    @Test
    void ignoresShenanigansFromAnEarlierAgenda() {
        AiPrompt stale = deadlyPlotAndBribery(NOW - 3_600_000L);
        ShenanigansRules.react(context(NOW, Set.of()));

        assertThat(ShenanigansRules.react(context(LATE, Set.of(), stale))).isEmpty();
    }

    @Test
    void theBrainAnswersThem() {
        AiPrompt plots = deadlyPlotAndBribery(NOW);
        new NekroBrain().decide(context(NOW, Set.of(), plots));

        assertThat(AiTestGame.pressedId(new NekroBrain().decide(context(LATE, Set.of(), plots))))
                .startsWith("generic_button_id_");
    }
}
