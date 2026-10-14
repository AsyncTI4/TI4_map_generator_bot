package ti4.service.testbed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.tree.MessageComponentTree;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import org.junit.jupiter.api.Test;
import ti4.model.TestBedScript.Expect;
import ti4.service.testbed.TestBedScriptRunner.WaitMode;

// Pieces of the script runner that decide how long an expect waits and what it can see.
class TestBedScriptRunnerTest {

    // Positive checks stop as soon as they pass; negative ones keep watching until the channel goes quiet, so late
    // output (map renders, queued sends) is still checked.
    @Test
    void waitModeFollowsTheKindOfCheck() {
        Expect contains = new Expect();
        contains.setIn("main");
        contains.setContains(List.of("Total hits"));
        assertEquals(WaitMode.UNTIL_PASS, TestBedScriptRunner.waitMode(contains));

        Expect leak = new Expect();
        leak.setIn("main");
        leak.setNoFactionLeak(true);
        assertEquals(WaitMode.UNTIL_QUIET, TestBedScriptRunner.waitMode(leak));

        Expect noButtons = new Expect();
        noButtons.setIn("main");
        noButtons.setNoButtons(List.of("End Turn"));
        assertEquals(WaitMode.UNTIL_QUIET, TestBedScriptRunner.waitMode(noButtons));

        Expect ephemeral = new Expect();
        ephemeral.setEphemeral("for someone else");
        assertEquals(WaitMode.ONCE, TestBedScriptRunner.waitMode(ephemeral));

        Expect state = new Expect();
        state.setState("game.round");
        state.setEquals("2");
        assertEquals(WaitMode.UNTIL_PASS, TestBedScriptRunner.waitMode(state));
    }

    // Embed fields, footer and author are part of the checked text, not just title and description.
    @Test
    void messageTextIncludesWholeEmbeds() {
        MessageEmbed embed = new EmbedBuilder()
                .setAuthor("Combat")
                .setTitle("Round 1")
                .setDescription("Space combat")
                .addField("Hits", "Total hits 2", false)
                .setFooter("rolled by sol")
                .build();
        Message message = mock(Message.class);
        when(message.getContentRaw()).thenReturn("Roll");
        when(message.getEmbeds()).thenReturn(List.of(embed));

        String text = TestBedScriptRunner.messageText(message);
        for (String part :
                List.of("Roll", "Combat", "Round 1", "Space combat", "Hits", "Total hits 2", "rolled by sol")) {
            assertTrue(text.contains(part), part);
        }
    }

    @Test
    void regexChecksSpanLinesAndRejectBadPatterns() {
        assertTrue(TestBedScriptRunner.anyMatches(List.of("Total hits 2\nmore"), "hits \\d+.*more"));
        assertFalse(TestBedScriptRunner.anyMatches(List.of("Total hits 2"), "hits [a-z]"));
        assertFalse(TestBedScriptRunner.anyMatches(List.of("x"), "("));
    }

    // Disabled buttons do not count as present.
    @Test
    void findsEnabledButtonsByLabelOrId() {
        Message message = mock(Message.class);
        when(message.getComponentTree())
                .thenReturn(MessageComponentTree.of(ActionRow.of(
                        Button.danger("FFCC_sol_turnEnd", "End Turn"),
                        Button.primary("FFCC_sol_passForRound", "Pass").asDisabled())));

        assertEquals(
                1,
                TestBedScriptRunner.matchingButtons(List.of(message), "End Turn")
                        .size());
        assertEquals(
                1,
                TestBedScriptRunner.matchingButtons(List.of(message), "turnEnd").size());
        assertEquals(
                0, TestBedScriptRunner.matchingButtons(List.of(message), "Pass").size());
    }
}
