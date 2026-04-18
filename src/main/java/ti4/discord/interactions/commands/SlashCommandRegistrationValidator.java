package ti4.discord.interactions.commands;

import java.util.Collection;
import java.util.List;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

final class SlashCommandRegistrationValidator {
    private static final int MAX_SUBCOMMANDS_OR_GROUPS_PER_COMMAND = 25;
    private static final int MAX_SUBCOMMANDS_PER_GROUP = 25;

    private SlashCommandRegistrationValidator() {}

    static void validateTopLevelSlashCommandCount(Collection<? extends ParentCommand> commands) {
        if (commands.size() > Commands.MAX_SLASH_COMMANDS) {
            throw new SlashCommandRegistrationException("Attempted to register " + commands.size()
                    + " top-level slash commands, but Discord only allows " + Commands.MAX_SLASH_COMMANDS + ".");
        }
    }

    static void validateCommandStructure(
            String commandName,
            Collection<? extends Subcommand> subcommands,
            Collection<? extends SubcommandGroup> subcommandGroups,
            List<OptionData> options) {
        requireNonNull(commandName, subcommands, "subcommands");
        requireNonNull(commandName, subcommandGroups, "subcommand groups");
        requireNonNull(commandName, options, "options");

        if (subcommands.size() > MAX_SUBCOMMANDS_OR_GROUPS_PER_COMMAND) {
            throw new SlashCommandRegistrationException("Slash command `/" + commandName + "` attempted to register "
                    + subcommands.size() + " subcommands, but Discord only allows "
                    + MAX_SUBCOMMANDS_OR_GROUPS_PER_COMMAND + ".");
        }

        if (subcommandGroups.size() > MAX_SUBCOMMANDS_OR_GROUPS_PER_COMMAND) {
            throw new SlashCommandRegistrationException("Slash command `/" + commandName + "` attempted to register "
                    + subcommandGroups.size() + " subcommand groups, but Discord only allows "
                    + MAX_SUBCOMMANDS_OR_GROUPS_PER_COMMAND + ".");
        }

        if (subcommands.size() + subcommandGroups.size() > MAX_SUBCOMMANDS_OR_GROUPS_PER_COMMAND) {
            throw new SlashCommandRegistrationException("Slash command `/" + commandName + "` attempted to register "
                    + (subcommands.size() + subcommandGroups.size())
                    + " subcommands and subcommand groups, but Discord only allows "
                    + MAX_SUBCOMMANDS_OR_GROUPS_PER_COMMAND + " total at that level.");
        }

        if (!options.isEmpty() && (!subcommands.isEmpty() || !subcommandGroups.isEmpty())) {
            throw new SlashCommandRegistrationException("Slash command `/" + commandName
                    + "` cannot register both top-level options and subcommands/subcommand groups.");
        }

        for (SubcommandGroup subcommandGroup : subcommandGroups) {
            validateGroupSubcommandCount(commandName, subcommandGroup.getName(), subcommandGroup.getSubcommands());
        }
    }

    static void validateGroupSubcommandCount(String groupName, Collection<? extends Subcommand> subcommands) {
        requireNonNull(groupName, subcommands, "group subcommands");

        if (subcommands.size() > MAX_SUBCOMMANDS_PER_GROUP) {
            throw new SlashCommandRegistrationException("Subcommand group `" + groupName + "` attempted to register "
                    + subcommands.size() + " subcommands, but Discord only allows " + MAX_SUBCOMMANDS_PER_GROUP + ".");
        }
    }

    static void validateGroupSubcommandCount(
            String parentCommandName, String groupName, Collection<? extends Subcommand> subcommands) {
        requireNonNull(parentCommandName, subcommands, "group subcommands");

        if (subcommands.size() > MAX_SUBCOMMANDS_PER_GROUP) {
            throw new SlashCommandRegistrationException("Slash command `/" + parentCommandName + "` group `"
                    + groupName + "` attempted to register " + subcommands.size()
                    + " subcommands, but Discord only allows " + MAX_SUBCOMMANDS_PER_GROUP + ".");
        }
    }

    private static void requireNonNull(String commandName, Object value, String fieldName) {
        if (value == null) {
            throw new SlashCommandRegistrationException(
                    "Slash command `/" + commandName + "` returned null " + fieldName + ".");
        }
    }
}
