package ti4.ai.xxcha;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.ai.AiTestGame.NOW;
import static ti4.ai.AiTestGame.prompt;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.brain.Prompts;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.game.Player;
import ti4.testUtils.BaseTi4Test;

class XxchaAgentRulesTest extends BaseTi4Test {

    private AiTestGame test;
    private Player xxcha;

    // Xxcha holds its home (Archon Ren 2/3, Archon Tau 1/1) and Fria (2 resources), all ready, and Abyz (3 resources),
    // exhausted. Erect a Monument takes 8 resources: the 5 ready plus Abyz.
    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        xxcha = test.addSeat(AiTestGame.SECOND_AI_ID, "xxcha", "green");
        test.isActive(xxcha, "action");
        test.place("14", "301");
        test.place("38", "201");
        for (String planet : List.of("archonren", "archontau", "fria", "abyz")) xxcha.addPlanet(planet);
        xxcha.exhaustPlanet("abyz");
        xxcha.addLeader("xxchaagent");
        xxcha.setTacticalCC(3);
        test.game.getRevealedPublicObjectives().put("monument", 1);
    }

    private AiTurnContext context(AiPrompt... prompts) {
        return test.contextFor(xxcha, Set.of(), NOW, prompts);
    }

    private static AiPrompt turnMenu() {
        return prompt(
                "turn",
                PromptSource.PUBLIC,
                NOW,
                "FFCC_xxcha_tacticalAction",
                "FFCC_xxcha_componentAction",
                "FFCC_xxcha_passForRound");
    }

    private static AiPrompt componentMenu() {
        return prompt(
                "menu",
                PromptSource.AI_THREAD,
                NOW,
                "FFCC_xxcha_componentActionRes_leader_xxchaagent",
                "FFCC_xxcha_componentActionRes_ability_generic",
                "deleteButtons");
    }

    private static AiPrompt userChoice() {
        return prompt(
                "users", PromptSource.PUBLIC, NOW, "exhaustAgent_xxchaagent_xxcha", "exhaustAgent_xxchaagent_sol");
    }

    private static AiPrompt planetChoice() {
        return prompt(
                "planets",
                PromptSource.PUBLIC,
                NOW,
                "khraskHeroStep4Ready_xxcha_abyz",
                "khraskHeroStep4Ready_xxcha_fria",
                "khraskHeroStep4Ready_sol_jord");
    }

    private static String pressed(Optional<AiDecision> decision) {
        return decision.map(AiTestGame::pressedId).orElse("");
    }

    private Optional<AiDecision> beforePassing() {
        AiTurnContext context = context(turnMenu());
        return XxchaAgentRules.beforePassing(context, Prompts.thisTurn(context));
    }

    // Readying Abyz is exactly what Erect a Monument needs, and the agent only costs an action, so the Xxcha use it
    // in place of passing and ready the exhausted planet that completes the payment.
    @Test
    void readiesTheExhaustedPlanetThatUnlocksASpendObjectiveBeforePassing() {
        assertThat(pressed(beforePassing())).isEqualTo("FFCC_xxcha_componentAction");

        assertThat(pressed(XxchaAgentRules.next(context(componentMenu()))))
                .isEqualTo("FFCC_xxcha_componentActionRes_leader_xxchaagent");
        assertThat(pressed(XxchaAgentRules.next(context(userChoice())))).isEqualTo("exhaustAgent_xxchaagent_xxcha");
        assertThat(pressed(XxchaAgentRules.next(context(planetChoice())))).isEqualTo("khraskHeroStep4Ready_xxcha_abyz");
        assertThat(XxchaAgentRules.next(context(planetChoice()))).isEmpty();
    }

    // While the agent is mid-way through the button chain the turn must not end, so the bot waits for the next prompt.
    @Test
    void waitsForTheNextPromptInsteadOfEndingTheTurn() {
        beforePassing();

        assertThat(XxchaAgentRules.next(context())).containsInstanceOf(AiDecision.Wait.class);
    }

    @Test
    void doesNothingWhenNoReadiedPlanetWouldBeUsedAndNobodyIsLeftToStallAgainst() {
        test.game.getRevealedPublicObjectives().clear();
        test.sol.setPassed(true);
        test.nekro.setPassed(true);

        assertThat(beforePassing()).isEmpty();
    }

    @Test
    void doesNothingWithNothingExhausted() {
        xxcha.refreshPlanet("abyz");

        assertThat(beforePassing()).isEmpty();
    }

    @Test
    void doesNothingOnceTheAgentIsExhausted() {
        xxcha.getLeader("xxchaagent").orElseThrow().setExhausted(true);

        assertThat(beforePassing()).isEmpty();
    }

    @Test
    void doesNothingOutsideItsOwnTurn() {
        test.isActive(test.sol, "action");

        assertThat(beforePassing()).isEmpty();
    }

    // Archon Ren is 2 resources and 3 influence: readying it adds 2 to the payment, 5 with the hero. Only with the hero
    // does the one readying complete Erect a Monument, and the agent picks Archon Ren over Archon Tau.
    @Test
    void theHeroMakesAHomePlanetWorthReadying() {
        test.sol.setPassed(true);
        test.nekro.setPassed(true);
        xxcha.refreshPlanet("abyz");
        xxcha.exhaustPlanet("archonren");
        xxcha.exhaustPlanet("archontau");
        assertThat(beforePassing()).isEmpty();

        xxcha.getLeader("xxchahero").ifPresentOrElse(hero -> hero.setLocked(false), () -> {
            xxcha.addLeader("xxchahero");
            xxcha.getLeader("xxchahero").orElseThrow().setLocked(false);
        });

        assertThat(pressed(beforePassing())).isEqualTo("FFCC_xxcha_componentAction");
        AiPrompt planets = prompt(
                "planets",
                PromptSource.PUBLIC,
                NOW,
                "khraskHeroStep4Ready_xxcha_archontau",
                "khraskHeroStep4Ready_xxcha_archonren");
        assertThat(pressed(XxchaAgentRules.next(context(componentMenu()))))
                .isEqualTo("FFCC_xxcha_componentActionRes_leader_xxchaagent");
        assertThat(pressed(XxchaAgentRules.next(context(userChoice())))).isEqualTo("exhaustAgent_xxchaagent_xxcha");
        assertThat(pressed(XxchaAgentRules.next(context(planets)))).isEqualTo("khraskHeroStep4Ready_xxcha_archonren");
    }

    // Someone is still acting, so an action that spends no token is worth something even when the planet it readies
    // has no use: the agent is the stall.
    @Test
    void stallsWithTheAgentWhileOthersAreStillActing() {
        test.game.getRevealedPublicObjectives().clear();

        assertThat(pressed(beforePassing())).isEqualTo("FFCC_xxcha_componentAction");
    }
}
