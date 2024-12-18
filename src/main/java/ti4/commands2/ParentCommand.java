package ti4.commands2;

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
        String subcommandName = event.getInteraction().getSubcommandName();
        Subcommand subcommand = getSubcommands().get(subcommandName);
        return subcommand == null || subcommand.accept(event);
    }

    default void execute(SlashCommandInteractionEvent event) {
        String subcommandName = event.getInteraction().getSubcommandName();
        Subcommand subcommand = getSubcommands().get(subcommandName);
        subcommand.preExecute(event);
        subcommand.execute(event);
        subcommand.postExecute(event);
    }

    default void register(CommandListUpdateAction commands) {
        var command = Commands.slash(getName(), getDescription())
                .addSubcommands(getSubcommands().values())
                .addOptions(getOptions());
        commands.addCommands(command);
    }

    String getDescription();

    default Map<String, Subcommand> getSubcommands() {
        return Collections.emptyMap();
    }

    default List<OptionData> getOptions() {
        return Collections.emptyList();
    }
}
