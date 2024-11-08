package ti4.commands.tigl;

import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import ti4.commands.Command;
import ti4.helpers.Constants;


public class TIGLCommand implements Command {

    private final Collection<TIGLSubcommandData> subcommandData = getSubcommands();

    @Override
    public String getActionID() {
        return Constants.TIGL;
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String subcommandName = event.getInteraction().getSubcommandName();
        TIGLSubcommandData executedCommand = null;
        for (TIGLSubcommandData subcommand : subcommandData) {
            if (Objects.equals(subcommand.getName(), subcommandName)) {
                subcommand.preExecute(event);
                subcommand.execute(event);
                executedCommand = subcommand;
                break;
            }
        }
        if (executedCommand == null) {
            reply(event);
        } else {
            executedCommand.reply(event);
        }
    }

    public static void reply(SlashCommandInteractionEvent event) {
    }

    protected String getActionDescription() {
        return "Twilight Imperium Global League (TIGL)";
    }

    private Collection<TIGLSubcommandData> getSubcommands() {
        Collection<TIGLSubcommandData> subcommands = new HashSet<>();
        subcommands.add(new TIGLShowHeroes());

        return subcommands;
    }

    @Override
    public void registerCommands(CommandListUpdateAction commands) {
        commands.addCommands(Commands.slash(getActionID(), getActionDescription()).addSubcommands(getSubcommands()));
    }
}
