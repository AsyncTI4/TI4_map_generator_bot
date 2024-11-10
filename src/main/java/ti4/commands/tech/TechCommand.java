package ti4.commands.tech;

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

public class TechCommand implements Command {

    private final Collection<TechSubcommandData> subcommandData = getSubcommands();

    @Override
    public String getActionId() {
        return Constants.TECH;
    }

    @Override
    public boolean accept(SlashCommandInteractionEvent event) {
        return SlashCommandAcceptanceHelper.shouldAcceptIfActivePlayerOfGame(getActionId(), event);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String subcommandName = event.getInteraction().getSubcommandName();
        TechSubcommandData executedCommand = null;
        for (TechSubcommandData subcommand : subcommandData) {
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
        return "Add/remove/exhaust/ready Technologies";
    }

    private Collection<TechSubcommandData> getSubcommands() {
        Collection<TechSubcommandData> subcommands = new HashSet<>();
        subcommands.add(new TechAdd());
        subcommands.add(new TechRemove());
        subcommands.add(new TechExhaust());
        subcommands.add(new TechRefresh());
        subcommands.add(new TechInfo());
        subcommands.add(new GetTechButton());
        subcommands.add(new TechChangeType());
        subcommands.add(new TechShowDeck());

        return subcommands;
    }

    @Override
    public void registerCommands(CommandListUpdateAction commands) {
        commands.addCommands(
            Commands.slash(getActionId(), getActionDescription())
                .addSubcommands(getSubcommands()));
    }

}
