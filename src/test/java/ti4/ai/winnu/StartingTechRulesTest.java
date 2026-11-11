package ti4.ai.winnu;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.ai.AiTestGame.NOW;
import static ti4.ai.AiTestGame.prompt;

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

// The Winnu start without a technology: setup posts the faction's starting options (no-prerequisite technologies) as
// free "getTech_<alias>__noPay__comp" buttons owned by the seat, in its actions channel, and the seat must pick one.
class StartingTechRulesTest extends BaseTi4Test {

    private static final String PREFIX = "FFCC_winnu_getTech_";
    private static final String SUFFIX = "__noPay__comp";

    private WinnuTestBoard board;
    private AiPrompt options;

    @BeforeEach
    void setUp() {
        board = new WinnuTestBoard();
        board.test.game.setRound(1);
        options = prompt(
                "start",
                PromptSource.PUBLIC,
                NOW,
                PREFIX + "amd" + SUFFIX,
                PREFIX + "nm" + SUFFIX,
                PREFIX + "pa" + SUFFIX,
                PREFIX + "sdn" + SUFFIX,
                PREFIX + "st" + SUFFIX);
    }

    @Test
    void picksOneOfTheOfferedStartingTechnologies() {
        Optional<AiDecision> pick = StartingTechRules.choose(context(options));

        assertThat(pick.map(AiTestGame::pressedId))
                .hasValueSatisfying(id -> assertThat(id).startsWith(PREFIX).endsWith(SUFFIX));
    }

    // The brain picks it too, with no turn of its own: setup happens before the game starts.
    @Test
    void theBrainPicksItBeforeTheGameStarts() {
        AiDecision decision = new NekroBrain().decide(context(options));

        assertThat(AiTestGame.pressedId(decision)).startsWith(PREFIX).endsWith(SUFFIX);
    }

    @Test
    void stopsOnceItHasItsStartingTechnology() {
        board.winnu.addTech("nm");

        assertThat(StartingTechRules.choose(context(options))).isEmpty();
    }

    // A later free technology prompt is someone else's business (research, relics, action cards).
    @Test
    void onlyDuringSetup() {
        board.test.game.setRound(2);

        assertThat(StartingTechRules.choose(context(options))).isEmpty();
    }

    // Factions with a fixed starting technology have no options to choose from.
    @Test
    void ignoresFactionsWithoutStartingOptions() {
        AiTurnContext sol = board.test.contextFor(board.test.sol, Set.of(), NOW, options);

        assertThat(StartingTechRules.choose(sol)).isEmpty();
    }

    private AiTurnContext context(AiPrompt... prompts) {
        return board.test.contextFor(board.winnu, Set.of(), NOW, prompts);
    }
}
