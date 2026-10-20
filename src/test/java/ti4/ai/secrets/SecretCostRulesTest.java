package ti4.ai.secrets;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.ai.AiTestGame.NOW;
import static ti4.ai.AiTestGame.prompt;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.testUtils.BaseTi4Test;

class SecretCostRulesTest extends BaseTi4Test {

    private static final long HOURS_LATER = 3 * 60 * 60_000L;

    private AiTestGame test;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.game.setPhaseOfGame("statusScoring");
    }

    // Form a Spy Network costs 5 action cards. The bot only posts discard buttons and trusts the player, so after
    // scoring it the AI discards until it has 5 fewer cards.
    @Test
    void discardsFiveActionCardsAfterScoringFormASpyNetwork() {
        for (int card = 1; card <= 6; card++) test.nekro.setActionCard("sabo" + card, card);
        test.nekro.setSecret("fsn");
        SecretCostRules.expect(test.context(), "fsn");
        score("fsn");
        AiPrompt discards = prompt("discards", PromptSource.AI_THREAD, NOW, "ac_discard_from_hand_3retain");

        assertThat(pressed(discards)).contains("ac_discard_from_hand_3retain");

        while (test.nekro.getAcCount() > 1) {
            test.nekro
                    .getActionCards()
                    .remove(test.nekro.getActionCards().keySet().iterator().next());
        }
        assertThat(pressed(discards)).isEmpty();
    }

    // Destroy Heretical Works costs 2 relic fragments; with more than 2 the bot asks which to purge.
    @Test
    void purgesTwoFragmentsThenFinishes() {
        test.nekro.addFragment("crf1");
        test.nekro.addFragment("crf2");
        test.nekro.addFragment("irf1");
        test.nekro.setSecret("dhw");
        SecretCostRules.expect(test.context(), "dhw");
        score("dhw");
        AiPrompt purge = prompt(
                "purge",
                PromptSource.PUBLIC,
                NOW,
                List.of("FFCC_nekro_purge_Frags_CRF_1", "FFCC_nekro_deleteButtons"),
                List.of("Purge 1 Cultural Fragment", "Done Purging"));

        assertThat(pressed(purge)).contains("FFCC_nekro_purge_Frags_CRF_1");

        test.nekro.getFragments().remove("crf1");
        test.nekro.getFragments().remove("crf2");
        assertThat(pressed(purge)).contains("FFCC_nekro_deleteButtons");
    }

    // Late in the game the bot can queue a secret until earlier players in scoring order decide, which may take
    // hours. The cost is owed from when the secret is actually scored, however long that takes.
    @Test
    void paysForAQueuedSecretWhenItIsFinallyScored() {
        for (int card = 1; card <= 6; card++) test.nekro.setActionCard("sabo" + card, card);
        test.nekro.setSecret("fsn");
        SecretCostRules.expect(test.context(), "fsn");
        AiPrompt discards = prompt("discards", PromptSource.AI_THREAD, NOW, "ac_discard_from_hand_3retain");
        assertThat(SecretCostRules.pay(test.contextAt(NOW, discards))).isEmpty();

        score("fsn");
        AiPrompt later = prompt("later", PromptSource.AI_THREAD, NOW + HOURS_LATER, "ac_discard_from_hand_3retain");

        assertThat(SecretCostRules.pay(test.contextAt(NOW + HOURS_LATER, later)).map(AiTestGame::pressedId))
                .contains("ac_discard_from_hand_3retain");
    }

    private void score(String secret) {
        test.game.scoreSecretObjective(
                test.nekro.getUserID(), test.nekro.getSecretsUnscored().get(secret));
    }

    private Optional<String> pressed(AiPrompt prompt) {
        return SecretCostRules.pay(test.context(prompt)).map(AiTestGame::pressedId);
    }
}
