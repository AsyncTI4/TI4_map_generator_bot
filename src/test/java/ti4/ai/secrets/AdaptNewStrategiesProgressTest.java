package ti4.ai.secrets;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.ai.AiTestGame.NOW;
import static ti4.ai.AiTestGame.prompt;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.service.info.ListPlayerInfoService;
import ti4.testUtils.BaseTi4Test;

// Adapt New Strategies: "Own 2 faction technologies", Valefar Assimilator technologies excluded. The AI must count
// exactly what the bot's own scoring check counts, or it plans for points the bot will not give it.
class AdaptNewStrategiesProgressTest extends BaseTi4Test {

    private AiTestGame test;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.sol.setSecret("ans");
    }

    @Test
    void theAiCountMatchesTheBotCheckForOwnFactionTechnologies() {
        test.sol.addTech("ac2");
        test.sol.addTech("so2");

        assertThat(SecretValue.ownFactionTechnologies(test.sol)).isEqualTo(2);
        assertThat(botProgress()).isEqualTo(2);
    }

    // The bot counts any faction technology, whoever's it is; Letnev's Non-Euclidean Shielding counts for Sol.
    @Test
    void aForeignFactionTechnologyCountsInBothPlaces() {
        test.sol.addTech("ac2");
        test.sol.addTech("nes");

        assertThat(SecretValue.ownFactionTechnologies(test.sol))
                .isEqualTo(botProgress())
                .isEqualTo(2);
    }

    @Test
    void valefarAssimilatorsAndGenericTechnologiesCountInNeitherPlace() {
        test.sol.addTech("vax");
        test.sol.addTech("vay");
        test.sol.addTech("md");

        assertThat(SecretValue.ownFactionTechnologies(test.sol)).isEqualTo(botProgress());
        assertThat(botProgress()).isZero();
    }

    @Test
    void theAiDoesNotCallTheSecretScorableOnATechnologyTheBotDoesNotCount() {
        test.sol.addTech("ac2");
        test.sol.addTech("vax");

        assertThat(SecretValue.bestScorableStatusSecret(test.game, test.sol)).isEmpty();
    }

    @Test
    void aForeignFactionTechnologyMakesTheSecretScorable() {
        test.sol.addTech("ac2");
        test.sol.addTech("nes");

        assertThat(SecretValue.bestScorableStatusSecret(test.game, test.sol)).contains("ans");
    }

    // The bot marks a seat that cannot yet score as having opted out when scoring opens, before the Entropic Scar
    // technology is bought. A secret that becomes scorable afterwards must still be scored.
    @Test
    void scoresAfterTheBotHasMarkedTheSeatAsNotScoring() {
        test.game.setPhaseOfGame("statusScoring");
        test.sol.addTech("ac2");
        test.sol.addTech("so2");
        optOut();
        int hand = test.sol.getSecretsUnscored().get("ans");
        AiPrompt score = prompt("score", PromptSource.AI_THREAD, NOW, "so_score_hand_" + hand);

        assertThat(SecretRules.scoreStatusSecret(test.contextFor(test.sol, java.util.Set.of(), NOW, score))
                        .map(AiTestGame::pressedId))
                .contains("so_score_hand_" + hand);
    }

    @Test
    void doesNotPressNoScoringAgainWhenOptedOutWithNothingToScore() {
        test.game.setPhaseOfGame("statusScoring");
        optOut();
        AiPrompt status = prompt("status", PromptSource.PUBLIC, NOW, "so_no_scoring", "get_so_score_buttons");

        assertThat(SecretRules.scoreStatusSecret(test.contextFor(test.sol, java.util.Set.of(), NOW, status)))
                .isEmpty();
    }

    @Test
    void doesNotScoreOnceTheSeatHasAlreadyScoredOrQueued() {
        test.game.setPhaseOfGame("statusScoring");
        test.sol.addTech("ac2");
        test.sol.addTech("so2");
        test.game.setStoredValue(test.sol.getFaction() + "round" + test.game.getRound() + "SO", "Queued");
        int hand = test.sol.getSecretsUnscored().get("ans");
        AiPrompt score = prompt("score", PromptSource.AI_THREAD, NOW, "so_score_hand_" + hand);

        assertThat(SecretRules.scoreStatusSecret(test.contextFor(test.sol, java.util.Set.of(), NOW, score)))
                .isEmpty();
    }

    private void optOut() {
        test.game.setStoredValue(test.sol.getFaction() + "round" + test.game.getRound() + "SO", "None");
    }

    private int botProgress() {
        return ListPlayerInfoService.getPlayerProgressOnObjective("ans", test.game, test.sol);
    }
}
