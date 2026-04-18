package ti4.discord.interactions.commands;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.junit.jupiter.api.Test;

class SlashCommandRegistrationValidatorTest {

    @Test
    void validateTopLevelSlashCommandCountRejectsTooManyCommands() {
        Collection<? extends ParentCommand> commands = IntStream.range(0, Commands.MAX_SLASH_COMMANDS + 1)
                .mapToObj(index -> new TestParentCommand("command" + index))
                .toList();

        SlashCommandRegistrationException exception = assertThrows(
                SlashCommandRegistrationException.class,
                () -> SlashCommandRegistrationValidator.validateTopLevelSlashCommandCount(commands));

        assertEquals(
                "Attempted to register 101 top-level slash commands, but Discord only allows 100.",
                exception.getMessage());
    }

    @Test
    void validateCommandStructureRejectsTooManySubcommands() {
        TestParentCommand command = new TestParentCommand("too-many-subs", buildSubcommands(26), Map.of(), List.of());

        SlashCommandRegistrationException exception = assertThrows(
                SlashCommandRegistrationException.class,
                () -> SlashCommandRegistrationValidator.validateCommandStructure(
                        command.getName(),
                        command.getSubcommands().values(),
                        command.getSubcommandGroups().values(),
                        command.getOptions()));

        assertEquals(
                "Slash command `/too-many-subs` attempted to register 26 subcommands, but Discord only allows 25.",
                exception.getMessage());
    }

    @Test
    void validateCommandStructureRejectsOptionsAlongsideSubcommands() {
        TestParentCommand command = new TestParentCommand(
                "mixed-command",
                Map.of("sub", new TestSubcommand("sub")),
                Map.of(),
                List.of(new OptionData(OptionType.STRING, "name", "desc")));

        SlashCommandRegistrationException exception = assertThrows(
                SlashCommandRegistrationException.class,
                () -> SlashCommandRegistrationValidator.validateCommandStructure(
                        command.getName(),
                        command.getSubcommands().values(),
                        command.getSubcommandGroups().values(),
                        command.getOptions()));

        assertEquals(
                "Slash command `/mixed-command` cannot register both top-level options and subcommands/subcommand groups.",
                exception.getMessage());
    }

    @Test
    void subcommandGroupRejectsTooManySubcommands() {
        Map<String, Subcommand> groupSubcommands = buildSubcommands(26);

        SlashCommandRegistrationException exception = assertThrows(
                SlashCommandRegistrationException.class, () -> new SubcommandGroup("group", "description") {
                    @Override
                    public Map<String, Subcommand> getGroupSubcommands() {
                        return groupSubcommands;
                    }
                });

        assertEquals(
                "Subcommand group `group` attempted to register 26 subcommands, but Discord only allows 25.",
                exception.getMessage());
    }

    @Test
    void existingSlashCommandsStayWithinDiscordLimits() {
        assertDoesNotThrow(() -> {
            SlashCommandRegistrationValidator.validateTopLevelSlashCommandCount(SlashCommandManager.getCommands());
            SlashCommandManager.getCommands().forEach(SlashCommandRegistrationValidatorTest::validateRegisteredCommand);
        });
    }

    private static void validateRegisteredCommand(ParentCommand command) {
        SlashCommandRegistrationValidator.validateCommandStructure(
                command.getName(),
                command.getSubcommands().values(),
                command.getSubcommandGroups().values(),
                command.getOptions());
        SlashCommandRegistrationValidator.validateCommandStructure(
                command.getName(), command.getSearchSubcommands().values(), List.of(), command.getOptions());
    }

    private static Map<String, Subcommand> buildSubcommands(int count) {
        return IntStream.range(0, count)
                .mapToObj(index -> new TestSubcommand("sub" + index))
                .collect(Collectors.toMap(Subcommand::getName, subcommand -> subcommand));
    }

    private static final class TestParentCommand implements ParentCommand {
        private final String name;
        private final Map<String, Subcommand> subcommands;
        private final Map<String, SubcommandGroup> subcommandGroups;
        private final List<OptionData> options;

        private TestParentCommand(String name) {
            this(name, Map.of(), Map.of(), List.of());
        }

        private TestParentCommand(
                String name,
                Map<String, Subcommand> subcommands,
                Map<String, SubcommandGroup> subcommandGroups,
                List<OptionData> options) {
            this.name = name;
            this.subcommands = subcommands;
            this.subcommandGroups = subcommandGroups;
            this.options = options;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getDescription() {
            return "description";
        }

        @Override
        public Map<String, Subcommand> getSubcommands() {
            return subcommands;
        }

        @Override
        public Map<String, SubcommandGroup> getSubcommandGroups() {
            return subcommandGroups;
        }

        @Override
        public List<OptionData> getOptions() {
            return options;
        }
    }

    private static final class TestSubcommand extends Subcommand {
        private TestSubcommand(String name) {
            super(name, "description");
        }

        @Override
        public void execute(net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent event) {}
    }
}
