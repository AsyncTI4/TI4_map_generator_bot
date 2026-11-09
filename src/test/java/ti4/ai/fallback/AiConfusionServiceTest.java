package ti4.ai.fallback;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import net.dv8tion.jda.api.components.buttons.Button;
import org.junit.jupiter.api.Test;
import ti4.ai.AiExperiments;
import ti4.ai.AiExperiments.Experiment;
import ti4.ai.AiTestGame;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.PromptButton;
import ti4.game.Player;
import ti4.testUtils.BaseTi4Test;

class AiConfusionServiceTest extends BaseTi4Test {

    private final AiTestGame base = new AiTestGame();

    // For private choices the AI never shows its options to the table; it only ever declines.
    @Test
    void safeDefaultPrefersDecliningOptions() {
        AiPrompt prompt = prompt("playAC_sabo", "FFCC_nekro_deleteButtons", "FFCC_nekro_exhaustAgent_nekroagent");

        assertThat(AiConfusionService.safeDefault(base.game, base.nekro, prompt))
                .map(PromptButton::customId)
                .contains("FFCC_nekro_deleteButtons");
    }

    // Without a declining option there is no safe choice: never press something at random (it could play a card).
    @Test
    void safeDefaultIsEmptyWithoutADecliningOption() {
        AiPrompt prompt = prompt("ac_play_from_hand_12", "getDiscardButtonsACs");

        assertThat(AiConfusionService.safeDefault(base.game, base.nekro, prompt))
                .isEmpty();
    }

    // Buttons that open a form can't be completed by the AI, so they are never a safe choice.
    @Test
    void safeDefaultSkipsButtonsThatOpenAForm() {
        AiPrompt prompt = prompt("editRoundSummary_3~MDL", "declineSomething~MDL");

        assertThat(AiConfusionService.safeDefault(base.game, base.nekro, prompt))
                .isEmpty();
    }

    // Cancel-a-hit buttons contain "cancel" but throw away a free hit; they are never a safe choice.
    @Test
    void safeDefaultAndSelfPlayNeverCancelHits() {
        for (String handler :
                List.of("cancelSpaceHits_", "cancelPdsOffenseHits_", "cancelGroundHits_", "cancelAFBHits_")) {
            AiPrompt prompt = prompt(handler + "1_2");

            assertThat(AiConfusionService.safeDefault(base.game, base.nekro, prompt))
                    .isEmpty();
            assertThat(AiConfusionService.selfPlayChoice(base.game, base.nekro, prompt))
                    .isEmpty();
        }
    }

    // A seat with a tactic token or an unplayed strategy card left must not pass the round through the fallback,
    // even though "pass" is a declining hint; it waits for a better option instead.
    @Test
    void neverPassesTheRoundWhileItCanStillAct() {
        AiTestGame test = new AiTestGame();
        Player seat = test.nekro;
        seat.setTacticalCC(1);
        AiPrompt pass = prompt("FFCC_nekro_passForRound");

        assertThat(AiConfusionService.safeDefault(test.game, seat, pass)).isEmpty();
        assertThat(AiConfusionService.selfPlayChoice(test.game, seat, pass)).isEmpty();

        seat.setTacticalCC(0);
        seat.addSC(3);
        assertThat(AiConfusionService.safeDefault(test.game, seat, pass)).isEmpty();

        test.game.setSCPlayed(3, true);
        assertThat(AiConfusionService.safeDefault(test.game, seat, pass))
                .map(PromptButton::customId)
                .contains("FFCC_nekro_passForRound");
    }

    // End Turn only appears once the turn's action is done, so tokens left over are no reason to hold it back. The
    // end-of-turn abilities prompt puts End Turn first: the self-play fallback must end the turn, not fire an ability.
    @Test
    void endsAFinishedTurnRatherThanPressingAnEndOfTurnAbility() {
        AiTestGame test = new AiTestGame();
        Player seat = test.nekro;
        seat.setTacticalCC(3);
        AiPrompt endOfTurn = prompt("FFCC_nekro_turnEnd", "FFCC_nekro_exhaustAgent_nekroagent", "useRelic_nanoforge");

        assertThat(AiConfusionService.selfPlayChoice(test.game, seat, endOfTurn))
                .map(PromptButton::customId)
                .contains("FFCC_nekro_turnEnd");
    }

    // With the experiment off, the old fallback passes even with tactic tokens left.
    @Test
    void withoutTheGuardItStillPassesWithTokensLeft() {
        AiTestGame test = new AiTestGame();
        Player seat = test.nekro;
        seat.setTacticalCC(3);
        AiExperiments.disable(seat, EnumSet.of(Experiment.EXPLOIT_GUARDS));

        assertThat(AiConfusionService.safeDefault(test.game, seat, prompt("FFCC_nekro_passForRound")))
                .map(PromptButton::customId)
                .contains("FFCC_nekro_passForRound");
    }

    // Passing early, ending the turn and agenda votes are never handed to the table to choose.
    @Test
    void passEndTurnAndVotesAreNeverDelegated() {
        AiTestGame test = new AiTestGame();
        test.nekro.setTacticalCC(1);
        AiPrompt prompt = prompt(
                "FFCC_nekro_passForRound",
                "FFCC_nekro_turnEnd",
                "FFCC_nekro_vote",
                "FFCC_nekro_outcome_for",
                "FFCC_nekro_exhaustForVotes_planet_mr",
                "FFCC_nekro_deleteButtons");

        assertThat(prompt.buttons().stream()
                        .filter(button -> !AiConfusionService.neverDelegated(test.game, test.nekro, button))
                        .map(PromptButton::customId))
                .containsExactly("FFCC_nekro_deleteButtons");

        AiExperiments.disable(test.nekro, EnumSet.of(Experiment.EXPLOIT_GUARDS));
        assertThat(prompt.buttons().stream()
                        .noneMatch(button -> AiConfusionService.neverDelegated(test.game, test.nekro, button)))
                .isTrue();
    }

    // With no tactic token and every strategy card played, passing is the only legal move, so the table may press it.
    @Test
    void aForcedPassMayStillBeDelegated() {
        AiTestGame test = new AiTestGame();
        test.nekro.setTacticalCC(0);
        PromptButton pass = prompt("FFCC_nekro_passForRound").buttons().getFirst();

        assertThat(AiConfusionService.neverDelegated(test.game, test.nekro, pass))
                .isFalse();
    }

    // Only the game owner or a bot developer may choose for the AI; with the guard off, anyone may.
    @Test
    void onlyTheOwnerOrADeveloperMayChooseForTheAi() {
        AiTestGame test = new AiTestGame();

        assertThat(AiConfusionService.mayChooseFor(test.nekro, "owner", "owner", false))
                .isTrue();
        assertThat(AiConfusionService.mayChooseFor(test.nekro, "rival", "owner", true))
                .isTrue();
        assertThat(AiConfusionService.mayChooseFor(test.nekro, "rival", "owner", false))
                .isFalse();
        assertThat(AiConfusionService.mayChooseFor(test.nekro, "rival", null, false))
                .isFalse();

        AiExperiments.disable(test.nekro, EnumSet.of(Experiment.EXPLOIT_GUARDS));
        assertThat(AiConfusionService.mayChooseFor(test.nekro, "rival", "owner", false))
                .isTrue();
    }

    @Test
    void claimingADelegationSucceedsOnlyOnce() {
        assertThat(AiConfusionService.claim("delegation-message-1")).isTrue();
        assertThat(AiConfusionService.claim("delegation-message-1")).isFalse();
    }

    private static AiPrompt prompt(String... customIds) {
        List<PromptButton> buttons = new ArrayList<>();
        for (int i = 0; i < customIds.length; i++) {
            buttons.add(PromptButton.of(i, Button.secondary(customIds[i], "Option " + i)));
        }
        return new AiPrompt("channel", "message", AiPrompt.PromptSource.AI_THREAD, "", buttons, 0L);
    }
}
