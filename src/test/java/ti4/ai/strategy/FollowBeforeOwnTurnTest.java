package ti4.ai.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.ai.AiTestGame.NOW;
import static ti4.ai.AiTestGame.prompt;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.nekro.NekroBrain;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.testUtils.BaseTi4Test;

// When the holder of a strategy card ends their turn, the next player can become active before following it. An AI
// seat settles every secondary it still owes (follow or decline) before it starts its own action, so the players
// behind it in follow order are not left waiting through that action.
class FollowBeforeOwnTurnTest extends BaseTi4Test {

    private static final int IMPERIAL = 8;

    private AiTestGame test;
    private final NekroBrain brain = new NekroBrain();

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.nekroHome();
        test.game.setVp(10);
        test.sol.addSC(IMPERIAL);
        test.game.setSCPlayed(IMPERIAL, true);
        test.nekro.setStrategicCC(0);
        test.aiIsActive("action");
    }

    private static AiPrompt imperialCard() {
        return prompt("imperial", PromptSource.PUBLIC, NOW, "sc_follow_8", "sc_no_follow_8", "sc_draw_so");
    }

    private static AiPrompt turnStart() {
        return prompt("turn", PromptSource.PUBLIC, NOW, "FFCC_nekro_tacticalAction", "FFCC_nekro_turnEnd");
    }

    @Test
    void settlesAnOwedSecondaryBeforeStartingItsAction() {
        assertThat(AiTestGame.pressedId(brain.decide(test.context(imperialCard(), turnStart()))))
                .isEqualTo("sc_no_follow_8");
    }

    @Test
    void takesItsTurnOnceEverySecondaryIsSettled() {
        test.nekro.addFollowedSC(IMPERIAL);

        assertThat(AiTestGame.pressedId(brain.decide(test.context(imperialCard(), turnStart()))))
                .isNotIn("sc_no_follow_8", "sc_follow_8", "sc_draw_so");
    }
}
