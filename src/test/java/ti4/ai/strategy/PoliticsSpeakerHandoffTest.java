package ti4.ai.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.ai.AiTestGame.NOW;
import static ti4.ai.AiTestGame.prompt;

import java.util.EnumSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiExperiments;
import ti4.ai.AiTestGame;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.game.Player;
import ti4.testUtils.BaseTi4Test;

// Six seats in table order nekro, sol, hacan, xxcha, letnev, jolnar. Nekro plays Politics and must name a new
// speaker. Next round's picks start at the speaker and go round the table, so naming jolnar (the seat before nekro)
// lets nekro pick second, while naming sol (the seat after nekro) leaves it picking last.
class PoliticsSpeakerHandoffTest extends BaseTi4Test {

    private static final String PREFIX = "FFCC_nekro_sc_3_assign_speaker_to_";

    private AiTestGame test;
    private Player hacan;
    private Player xxcha;
    private Player letnev;
    private Player jolnar;
    private long modified = 1;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.nekroHome();
        hacan = test.addSeat("200000000000000002", "hacan", "yellow");
        xxcha = test.addSeat("200000000000000003", "xxcha", "green");
        letnev = test.addSeat("200000000000000004", "letnev", "red");
        jolnar = test.addSeat("200000000000000005", "jolnar", "purple");
        test.game.setVp(10);
        test.game.setSpeakerUserID(test.sol.getUserID());
        test.aiIsActive("action");
        for (Player rival : new Player[] {hacan, xxcha, letnev, jolnar}) test.points(rival, 3);
        changed();
    }

    private void changed() {
        test.game.setLastModifiedDate(modified++);
    }

    private String chooseSpeaker() {
        AiPrompt card = prompt("politics", PromptSource.PUBLIC, NOW, "sc_follow_3", "sc_no_follow_3", "sc_ac_draw");
        AiPrompt speaker = prompt(
                "speaker",
                PromptSource.PUBLIC,
                NOW,
                PREFIX + "sol",
                PREFIX + "hacan",
                PREFIX + "xxcha",
                PREFIX + "letnev",
                PREFIX + "jolnar");
        StrategyCardRules.play(test.context(card), card, card.buttons().getFirst(), 3);
        return StrategyCardRules.resolvePrimary(test.context(card, speaker))
                .map(AiTestGame::pressedId)
                .orElse("nothing");
    }

    // Sol trails on points, but handing it the token makes nekro pick last. Jolnar sits right before nekro.
    @Test
    void namesTheSeatRightBeforeItSoItPicksSecond() {
        assertThat(chooseSpeaker()).isEqualTo(PREFIX + "jolnar");
    }

    // Jolnar is two points from the goal and would pick first, so nekro names the next seat back instead.
    @Test
    void neverNamesARivalCloseToTheGoal() {
        test.points(jolnar, 5);
        changed();

        assertThat(chooseSpeaker()).isEqualTo(PREFIX + "letnev");
    }

    // The control seat keeps the old rule: the seat with the fewest points.
    @Test
    void controlSeatNamesTheLowestScorer() {
        AiExperiments.disable(test.nekro, EnumSet.of(AiExperiments.Experiment.SPEAKER_AND_TECH));
        changed();

        assertThat(chooseSpeaker()).isEqualTo(PREFIX + "sol");
    }
}
