package ti4.discord.interactions.commands;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

import java.util.ArrayList;
import java.util.List;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import ti4.contest.replay.core.CombatContestSettings;
import ti4.spring.context.SpringContext;

/**
 * Discord's per-command limits are enforced by JDA client-side, while the command is being built. A violation
 * therefore throws inside JdaService.startBot, which aborts registration of every command for that guild and skips
 * adding the guild to the whitelist. On 2026-09-20 a 26th /bothelper subcommand did exactly that in production and
 * the bot left every server it was in. These limits are checked here so the build fails first.
 */
class SlashCommandLimitsTest {

    // Subcommands, subcommand groups and top-level options all draw from the same 25 slots on a command.
    private static final int DISCORD_OPTION_SLOTS = 25;

    private static final int DISCORD_COMMANDS_PER_APP = 100;

    private static List<ParentCommand> commands;

    /**
     * Building the command list instantiates every command, and CombatCommand asks Spring whether replay debugging is
     * on. Stub that to the production answer (off) so the tree can be built without an application context.
     */
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
    void noParentCommandExceedsDiscordsOptionSlots() {
        List<String> overflowing = new ArrayList<>();
        for (ParentCommand command : commands) {
            int used = slotsUsedBy(command);
            if (used > DISCORD_OPTION_SLOTS) {
                overflowing.add("/" + command.getName() + " uses " + used);
            }
        }

        assertTrue(
                overflowing.isEmpty(),
                () -> "Over Discord's " + DISCORD_OPTION_SLOTS + " slots per command: " + overflowing
                        + ". Put the new subcommand under a different parent rather than freeing a slot.");
    }

    /**
     * Builds every command the way startup does, so JDA's own validation runs. Catches the subcommand cap plus the
     * other limits JDA checks, such as name and description lengths.
     */
    @Test
    void everyCommandBuildsTheWayStartupBuildsIt() {
        CommandListUpdateAction update = mock(CommandListUpdateAction.class);
        for (ParentCommand command : commands) {
            assertDoesNotThrow(() -> command.register(update), () -> "/" + command.getName() + " failed to build");
            assertDoesNotThrow(
                    () -> command.registerSearchCommands(update),
                    () -> "/" + command.getName() + " failed to build its search commands");
        }
    }

    @Test
    void theBotStaysUnderDiscordsCommandsPerAppLimit() {
        assertTrue(
                commands.size() <= DISCORD_COMMANDS_PER_APP,
                () -> "The bot registers " + commands.size() + " slash commands, over Discord's limit of "
                        + DISCORD_COMMANDS_PER_APP + ".");
    }

    private static int slotsUsedBy(ParentCommand command) {
        return command.getSubcommands().size()
                + command.getSubcommandGroups().size()
                + command.getOptions().size();
    }
}
