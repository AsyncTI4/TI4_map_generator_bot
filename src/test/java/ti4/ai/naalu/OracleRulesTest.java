package ti4.ai.naalu;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.ai.AiTestGame.NOW;
import static ti4.ai.AiTestGame.prompt;

import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.testUtils.BaseTi4Test;

// The Oracle: at the end of the status phase the bot reminds the holder in its private thread with an unowned
// "Play Naalu Hero" button. The hero has no other use, so the seat plays it at the first chance, as long as another
// player has a promissory note in hand to give.
class OracleRulesTest extends BaseTi4Test {

    private AiTestGame test;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.game.setRound(4);
        test.game.setPhaseOfGame("statusHomework");
        test.nekro.addLeader(OracleRules.HERO);
        test.nekro.unsafeGetLeader(OracleRules.HERO).setLocked(false);
        test.sol.setPromissoryNote("blue_ta");
    }

    private static AiPrompt reminder() {
        return prompt("oracle", PromptSource.AI_THREAD, NOW, OracleRules.HERO_BUTTON, "deleteButtons");
    }

    private String play(AiTurnContext context) {
        return OracleRules.play(context).map(AiTestGame::pressedId).orElse("");
    }

    @Test
    void playsTheOracleAtTheFirstChance() {
        assertThat(play(test.context(reminder()))).isEqualTo(OracleRules.HERO_BUTTON);
    }

    @Test
    void notWhileTheHeroIsLocked() {
        test.nekro.unsafeGetLeader(OracleRules.HERO).setLocked(true);

        assertThat(play(test.context(reminder()))).isEmpty();
    }

    // A note already in its holder's play area cannot be given, so it does not count.
    @Test
    void waitsWhileNobodyHasANoteToGive() {
        test.sol.addPromissoryNoteToPlayArea("blue_ta");

        assertThat(play(test.context(reminder()))).isEmpty();
    }

    @Test
    void pressesOnlyOnce() {
        AiPrompt reminder = reminder();
        Set<String> pressed =
                Set.of(AiTurnContext.pressKey(reminder, reminder.buttons().getFirst()));

        assertThat(play(test.context(pressed, reminder))).isEmpty();
    }

    // Only the bot's own reminder in the private thread counts, not a copy in a public channel.
    @Test
    void ignoresAPublicCopyOfTheButton() {
        AiPrompt publicCopy = prompt("oracle", PromptSource.PUBLIC, NOW, OracleRules.HERO_BUTTON);

        assertThat(play(test.context(publicCopy))).isEmpty();
    }
}
