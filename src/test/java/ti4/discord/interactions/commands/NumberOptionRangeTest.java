package ti4.discord.interactions.commands;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

import java.util.ArrayList;
import java.util.List;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandGroupData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import ti4.contest.replay.core.CombatContestSettings;
import ti4.spring.context.SpringContext;

/**
 * A number option without a range lets a typo like 1314000146 reach the game state. pbd26171 ended up with 1.3 billion
 * infantry that way and ran the bot out of memory. Every INTEGER and NUMBER option must say how big it can be, so
 * Discord rejects the value before the command runs.
 */
class NumberOptionRangeTest {

    private static List<ParentCommand> commands;

    @BeforeAll
    static void buildCommandsWithoutSpring() {
        CombatContestSettings settings = mock(CombatContestSettings.class, RETURNS_DEEP_STUBS);
        try (MockedStatic<SpringContext> spring = mockStatic(SpringContext.class)) {
            spring.when(() -> SpringContext.getBean(CombatContestSettings.class))
                    .thenReturn(settings);
            commands = List.copyOf(SlashCommandManager.getCommands());
        }
    }

    @Test
    void everyNumberOptionHasAMinimumAndMaximum() {
        List<String> unbounded = new ArrayList<>();
        for (ParentCommand command : commands) {
            String path = "/" + command.getName();
            collect(unbounded, path, command.getOptions());
            for (SubcommandData subcommand : command.getSubcommands().values()) {
                collect(unbounded, path + " " + subcommand.getName(), subcommand.getOptions());
            }
            for (SubcommandGroupData group : command.getSubcommandGroups().values()) {
                for (SubcommandData subcommand : group.getSubcommands()) {
                    collect(
                            unbounded,
                            path + " " + group.getName() + " " + subcommand.getName(),
                            subcommand.getOptions());
                }
            }
        }

        assertTrue(
                unbounded.isEmpty(),
                () -> unbounded.size() + " number options have no range; give each one setRequiredRange:\n"
                        + String.join("\n", unbounded));
    }

    private static void collect(List<String> unbounded, String path, List<OptionData> options) {
        for (OptionData option : options) {
            boolean isNumber = option.getType() == OptionType.INTEGER || option.getType() == OptionType.NUMBER;
            if (!isNumber || option.isAutoComplete() || !option.getChoices().isEmpty()) continue;
            if (option.getMinValue() == null || option.getMaxValue() == null) {
                unbounded.add(path + " " + option.getName() + " (" + option.getDescription() + ")");
            }
        }
    }
}
