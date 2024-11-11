package ti4.commands.installation;

import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import ti4.commands.Command;
import ti4.generator.MapRenderPipeline;
import ti4.helpers.Constants;
import ti4.helpers.SlashCommandAcceptanceHelper;
import ti4.map.Game;
import ti4.map.GameSaveLoadManager;
import ti4.message.MessageHelper;

public class InstallationCommand implements Command {

    private final Collection<InstallationSubcommandData> subcommandData = getSubcommands();

    @Override
    public String getActionId() {
        return Constants.INSTALLATION;
    }

    @Override
    public boolean accept(SlashCommandInteractionEvent event) {
        return SlashCommandAcceptanceHelper.acceptIfPlayerInGame(getActionId(), event);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String subcommandName = event.getInteraction().getSubcommandName();
        InstallationSubcommandData executedCommand = null;
        for (InstallationSubcommandData subcommand : subcommandData) {
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
        MessageHelper.replyToMessage(event, "Executed command. Use /show_game to check map");
    }

    protected String getActionDescription() {
        return "Installations";
    }

    private Collection<InstallationSubcommandData> getSubcommands() {
        Collection<InstallationSubcommandData> subcommands = new HashSet<>();
        subcommands.add(new AddSweepToken());
        subcommands.add(new RemoveSweepToken());
        return subcommands;
    }

    @Override
    public void registerCommands(CommandListUpdateAction commands) {
        SlashCommandData list = Commands.slash(getActionId(), getActionDescription()).addSubcommands(getSubcommands());
        commands.addCommands(list);
    }
}
