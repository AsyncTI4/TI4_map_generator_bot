package ti4.ai.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import net.dv8tion.jda.api.components.buttons.Button;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.ai.perception.PromptButton;
import ti4.ai.runtime.AiStallDetector.WaitReason;
import ti4.game.Game;
import ti4.game.Player;
import ti4.testUtils.BaseTi4Test;

class AiStallDetectorTest extends BaseTi4Test {

    private static final long NOW = 1_800_000_000_000L;
    private static final String AI_ID = "7100000123456789";

    private Game game;
    private Player nekro;

    @BeforeEach
    void setUp() {
        game = new Game();
        game.setName("stall-detector-test");
        game.setRound(1);
        nekro = game.addPlayer(AI_ID, "Nekro AI");
        nekro.setFaction("nekro");
        nekro.setColor("black");
    }

    // The fallback may only hand the table prompts tied to what the game is waiting on: during its turn that is
    // the AI's own buttons posted this turn, never its card hand or an older message.
    @Test
    void turnReasonOnlyCoversItsOwnPromptsFromThisTurn() {
        game.setActivePlayerID(AI_ID);
        game.setLastActivePlayerChange(new Date(NOW - 1000));
        AiPrompt current = prompt("current", PromptSource.PUBLIC, NOW, "FFCC_nekro_tacticalAction");
        AiPrompt older = prompt("older", PromptSource.PUBLIC, NOW - 3_600_000L, "FFCC_nekro_tacticalAction");
        AiPrompt hand = prompt("hand", PromptSource.AI_THREAD, NOW, "ac_play_from_hand_3");
        AiPrompt someoneElse = prompt("other", PromptSource.PUBLIC, NOW, "FFCC_sol_tacticalAction");

        List<WaitReason> reasons = AiStallDetector.waitReasons(game, nekro, List.of(current, older, hand, someoneElse));

        assertThat(reasons).extracting(WaitReason::description).containsExactly("its turn");
        assertThat(relatedTo(reasons, current, older, hand, someoneElse)).containsExactly("current");
    }

    @Test
    void scoringReasonsLastUntilBothChoicesAreRecorded() {
        game.setPhaseOfGame("statusScoring");
        AiPrompt scoring = prompt("scoring", PromptSource.PUBLIC, NOW, "po_no_scoring", "so_no_scoring");

        assertThat(AiStallDetector.waitReasons(game, nekro, List.of(scoring)))
                .extracting(WaitReason::description)
                .containsExactly("public objective scoring", "secret objective scoring");

        game.setStoredValue("nekroround1PO", "None");
        game.setStoredValue("nekroround1SO", "None");
        assertThat(AiStallDetector.waitReasons(game, nekro, List.of(scoring))).isEmpty();
    }

    // A combat counts only while a combat thread holds something for this seat to do, not merely because
    // factionsInCombat still names it, and not for buttons that belong to the opponent.
    @Test
    void combatReasonComesOnlyFromCombatPromptsTheSeatMustAnswer() {
        game.setStoredValue("factionsInCombat", "nekro_sol");
        AiPrompt mainChannel = prompt("main", PromptSource.PUBLIC, NOW, "FFCC_nekro_autoAssignSpaceHits_301_1");
        assertThat(AiStallDetector.waitReasons(game, nekro, List.of(mainChannel)))
                .isEmpty();

        AiPrompt opponents = prompt("theirs", PromptSource.COMBAT_THREAD, NOW, "FFCC_sol_autoAssignSpaceHits_301_1");
        assertThat(AiStallDetector.waitReasons(game, nekro, List.of(opponents))).isEmpty();

        AiPrompt combat = prompt("combat", PromptSource.COMBAT_THREAD, NOW, "FFCC_nekro_autoAssignSpaceHits_301_1");
        assertThat(AiStallDetector.waitReasons(game, nekro, List.of(combat)))
                .extracting(WaitReason::description)
                .containsExactly("a combat");
    }

    private static List<String> relatedTo(List<WaitReason> reasons, AiPrompt... prompts) {
        List<String> related = new ArrayList<>();
        for (AiPrompt prompt : prompts) {
            if (reasons.stream().anyMatch(reason -> reason.related().test(prompt))) related.add(prompt.messageId());
        }
        return related;
    }

    private static AiPrompt prompt(String messageId, PromptSource source, long created, String... customIds) {
        List<PromptButton> buttons = new ArrayList<>();
        for (int i = 0; i < customIds.length; i++) {
            buttons.add(PromptButton.of(i, Button.secondary(customIds[i], "Option " + i)));
        }
        return new AiPrompt("channel", messageId, source, "", buttons, created);
    }
}
