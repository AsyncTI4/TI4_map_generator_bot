package ti4.commands;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;

public interface ParentCommand extends Command {

    @Override
    default boolean accept(SlashCommandInteractionEvent event) {
        if (!Command.super.accept(event)) return false;
        String subcommandGroupName = event.getInteraction().getSubcommandGroup();
        if (subcommandGroupName == null) {
            String subcommandName = event.getInteraction().getSubcommandName();
            Subcommand subcommand = getSubcommands().get(subcommandName);
            return subcommand == null
                    || getSubcommands().containsKey(event.getInteraction().getSubcommandName());
        } else {
            SubcommandGroup subcommandGroup = getSubcommandGroups().get(subcommandGroupName);
            return subcommandGroup == null || getSubcommandGroups().containsKey(subcommandGroupName);
        }
    }

    default void execute(SlashCommandInteractionEvent event) {
        String subcommandGroupName = event.getInteraction().getSubcommandGroup();
        if (subcommandGroupName == null) {
            String subcommandName = event.getInteraction().getSubcommandName();
            Subcommand subcommand = getSubcommands().get(subcommandName);
            subcommand.preExecute(event);
            subcommand.execute(event);
            subcommand.postExecute(event);
        } else {
            SubcommandGroup subcommandGroup = getSubcommandGroups().get(subcommandGroupName);
            subcommandGroup.preExecute(event);
            subcommandGroup.execute(event);
            subcommandGroup.postExecute(event);
        }
    }

    @Override
    default boolean isSuspicious(SlashCommandInteractionEvent event) {
        String subcommandGroupName = event.getInteraction().getSubcommandGroup();
        if (subcommandGroupName == null) {
            String subcommandName = event.getInteraction().getSubcommandName();
            Subcommand subcommand = getSubcommands().get(subcommandName);
            return subcommand != null && subcommand.isSuspicious(event);
        }
        SubcommandGroup subcommandGroup = getSubcommandGroups().get(subcommandGroupName);
        return subcommandGroup != null && subcommandGroup.isSuspicious(event);
    }

    default void register(CommandListUpdateAction commands) {
        var command = Commands.slash(getName(), getDescription())
                .addSubcommands(getSubcommands().values())
                .addSubcommandGroups(getSubcommandGroups().values())
                .addOptions(getOptions());
        commands.addCommands(command);
    }

    default void registerSearchCommands(CommandListUpdateAction commands) {
        if (getSearchSubcommands().isEmpty()) return;
        var command = Commands.slash(getName(), getDescription())
                .addSubcommands(getSearchSubcommands().values())
                .addOptions(getOptions());
        commands.addCommands(command);
    }

    String getDescription();

    default Map<String, Subcommand> getSubcommands() {
        return Collections.emptyMap();
    }

    default Map<String, Subcommand> getSearchSubcommands() {
        return Collections.emptyMap();
    }

    default Map<String, SubcommandGroup> getSubcommandGroups() {
        return Collections.emptyMap();
    }

    default List<OptionData> getOptions() {
        return Collections.emptyList();
    }
}
