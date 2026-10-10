package ti4.ai.perception;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import net.dv8tion.jda.api.components.buttons.Button;
import org.junit.jupiter.api.Test;
import ti4.ai.nekro.NekroBrain;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.testUtils.BaseTi4Test;

class AiPerceptionTest extends BaseTi4Test {

    private static final Set<String> WINDOWS = Set.of("sc_no_follow_", "po_no_scoring");

    @Test
    void everythingInTheAisOwnThreadIsRelevant() {
        assertThat(AiPerception.isRelevant(PromptSource.AI_THREAD, buttons("queueAWhen"), "nekro", WINDOWS))
                .isTrue();
    }

    @Test
    void publicMessagesAreRelevantWhenOwnedByTheAiOrAKnownWindow() {
        assertThat(AiPerception.isRelevant(PromptSource.PUBLIC, buttons("FFCC_nekro_passForRound"), "nekro", WINDOWS))
                .isTrue();
        assertThat(AiPerception.isRelevant(
                        PromptSource.PUBLIC, buttons("sc_follow_3", "sc_no_follow_3"), "nekro", WINDOWS))
                .isTrue();
    }

    // Other players' owned buttons and unknown unowned buttons in public channels are none of the AI's business.
    @Test
    void ignoresOtherPlayersButtonsAndUnknownPublicButtons() {
        assertThat(AiPerception.isRelevant(PromptSource.PUBLIC, buttons("FFCC_sol_passForRound"), "nekro", WINDOWS))
                .isFalse();
        assertThat(AiPerception.isRelevant(PromptSource.PUBLIC, buttons("showGameAgain"), "nekro", WINDOWS))
                .isFalse();
    }

    // Integrated Economy and the exploration cards answer with unowned buttons in the main channel; the AI's public
    // windows must let it see them.
    @Test
    void seesItsIntegratedEconomyOfferAndExplorationCards() {
        Set<String> windows = new NekroBrain().publicWindowHandlerPrefixes();

        assertThat(AiPerception.isRelevant(
                        PromptSource.PUBLIC, buttons("integratedBuild_lodor", "deleteButtons"), "nekro", windows))
                .isTrue();
        assertThat(AiPerception.isRelevant(
                        PromptSource.PUBLIC, buttons("resolveVolatileInf_lodor", "decline_explore"), "nekro", windows))
                .isTrue();
    }

    // The AI must never react to its own "choose for the AI" messages.
    @Test
    void ignoresItsOwnDelegationMessages() {
        String delegation = AiPerception.DELEGATION_PREFIX + "7100000123456789_1_2_0_abcdef";
        assertThat(AiPerception.isRelevant(PromptSource.AI_THREAD, buttons(delegation), "nekro", WINDOWS))
                .isFalse();
        assertThat(AiPerception.isRelevant(PromptSource.PUBLIC, buttons(delegation), "nekro", WINDOWS))
                .isFalse();
    }

    // Combat threads carry the human's messages too (e.g. "I play Morale Boost"); the AI must see those even
    // though they have no buttons, so it can wait for them.
    @Test
    void combatThreadMessagesAreRelevantEvenWithoutButtons() {
        assertThat(AiPerception.isRelevant(PromptSource.COMBAT_THREAD, List.of(), "nekro", WINDOWS))
                .isTrue();
        assertThat(AiPerception.isRelevant(
                        PromptSource.COMBAT_THREAD, buttons("FFCC_sol_autoAssignSpaceHits_302_1"), "nekro", WINDOWS))
                .isTrue();
    }

    @Test
    void messagesWithoutButtonsOutsideCombatThreadsAreIgnored() {
        assertThat(AiPerception.isRelevant(PromptSource.AI_THREAD, List.of(), "nekro", WINDOWS))
                .isFalse();
        assertThat(AiPerception.isRelevant(PromptSource.PUBLIC, List.of(), "nekro", WINDOWS))
                .isFalse();
    }

    @Test
    void delegationMessagesAreNeverRelevantEvenInCombatThreads() {
        String delegation = AiPerception.DELEGATION_PREFIX + "7100000123456789_1_2_0_abcdef";
        assertThat(AiPerception.isRelevant(
                        PromptSource.COMBAT_THREAD, buttons("combatRoll_302_space", delegation), "nekro", WINDOWS))
                .isFalse();
    }

    private static List<PromptButton> buttons(String... customIds) {
        return java.util.stream.IntStream.range(0, customIds.length)
                .mapToObj(i -> PromptButton.of(i, Button.secondary(customIds[i], "Option " + i)))
                .toList();
    }
}
