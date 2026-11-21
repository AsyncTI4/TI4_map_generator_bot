package ti4.ai.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.testUtils.BaseTi4Test;

class ResearchPlanLogTest extends BaseTi4Test {

    private AiTestGame test;
    private List<String> lines;
    private ResearchPlanLog log;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.sol.setSecret("ans");
        lines = new ArrayList<>();
        log = new ResearchPlanLog(lines::add);
    }

    private List<String> solLines() {
        return lines.stream().filter(line -> line.contains(" sol r")).toList();
    }

    // Every seat gets a line at the first observation of a round, and an unchanged board prints nothing more.
    @Test
    void printsOncePerSeatAtTheStartOfARoundAndThenStaysQuiet() {
        log.observe(test.game);
        log.observe(test.game);
        log.observe(test.game);

        assertThat(lines).hasSize(2);
        assertThat(solLines()).hasSize(1);
        assertThat(solLines().getFirst())
                .startsWith("ResearchPlan ai-test-game sol r2: next ")
                .contains("| path [")
                .contains("| goals [ans (1 VP) needs 4 researches, feasible")
                .endsWith("| changed because first look");
        System.out.println(solLines().getFirst());
    }

    // A change within the round prints again and names the inputs that changed; a change that does not alter the
    // text prints nothing.
    @Test
    void printsAgainWhenThePlanChangesAndNamesTheChangedInputs() {
        log.observe(test.game);

        test.sol.addTech("gd");
        log.observe(test.game);

        assertThat(solLines()).hasSize(2);
        assertThat(solLines().getLast())
                .contains("ans (1 VP) needs 3 researches")
                .endsWith("changed because techs");
        System.out.println(solLines().getLast());
    }

    @Test
    void printsTheFirstLineOfEveryNewRoundEvenWhenNothingChanged() {
        log.observe(test.game);

        test.game.setRound(3);
        log.observe(test.game);

        assertThat(solLines()).hasSize(2);
        assertThat(solLines().getLast()).contains(" r3: ").endsWith("changed because new round");
    }

    @Test
    void aSecretOnlyChangeThatDoesNotChangeTheTextPrintsNothing() {
        log.observe(test.game);
        int printed = lines.size();

        test.sol.setSecret("fwp");
        log.observe(test.game);

        assertThat(lines).hasSize(printed);
    }

    @Test
    void isOffUnlessSwitchedOn() {
        // Off by default: rebuilding every seat's plan on each harness step cost about a fifth of a game's CPU.
        System.clearProperty(ResearchPlanLog.PROPERTY);
        assertThat(ResearchPlanLog.enabled()).isFalse();

        System.setProperty(ResearchPlanLog.PROPERTY, "true");
        try {
            assertThat(ResearchPlanLog.enabled()).isTrue();
        } finally {
            System.clearProperty(ResearchPlanLog.PROPERTY);
        }
    }
}
