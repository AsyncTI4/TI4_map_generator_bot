package ti4.commands2;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;

public interface ParentCommand extends Command {

    default void execute(SlashCommandInteractionEvent event) {
        String subcommandName = event.getInteraction().getSubcommandName();
        Subcommand subcommand = getSubcommands().get(subcommandName);
        if (subcommand != null && subcommand.accept(event)) {
            subcommand.preExecute(event);
            subcommand.execute(event);
            subcommand.postExecute(event);
        }
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
