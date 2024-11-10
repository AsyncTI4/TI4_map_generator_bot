package ti4.commands.capture;

import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import ti4.commands.Command;
import ti4.generator.MapRenderPipeline;
import ti4.helpers.Constants;
import ti4.helpers.SlashCommandAcceptanceHelper;
import ti4.map.Game;
import ti4.map.GameSaveLoadManager;

public class CaptureCommand implements Command {

    private final Collection<CaptureSubcommandData> subcommandData = getSubcommands();

    @Override
    public String getActionId() {
        return Constants.CAPTURE;
    }

    @Override
    public boolean accept(SlashCommandInteractionEvent event) {
        return SlashCommandAcceptanceHelper.shouldAcceptIfActivePlayerOfGame(getActionId(), event);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String subcommandName = event.getInteraction().getSubcommandName();
        CaptureSubcommandData executedCommand = null;
        for (CaptureSubcommandData subcommand : subcommandData) {
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
        String userID = event.getUser().getId();
        Game game = UserGameContextManager.getContextGame(userID);
        GameSaveLoadManager.saveGame(game, event);

        MapRenderPipeline.renderToWebsiteOnly(game, event);
    }

    protected String getActionDescription() {
        return "Capture units";
    }

    private Collection<CaptureSubcommandData> getSubcommands() {
        Collection<CaptureSubcommandData> subcommands = new HashSet<>();
        subcommands.add(new AddUnits());
        subcommands.add(new RemoveUnits());

        return subcommands;
    }

    @Override
    public void registerCommands(CommandListUpdateAction commands) {
        commands.addCommands(
            Commands.slash(getActionId(), getActionDescription())
                .addSubcommands(getSubcommands()));
    }
}
