package ti4.commands.leaders;

import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import ti4.commands.Command;
import ti4.helpers.Constants;
import ti4.helpers.SlashCommandAcceptanceHelper;
import ti4.map.Game;
import ti4.map.GameSaveLoadManager;

public class LeaderCommand implements Command {

    private final Collection<LeaderSubcommandData> subcommandData = getSubcommands();

    @Override
    public String getActionId() {
        return Constants.LEADERS;
    }

    @Override
    public boolean accept(SlashCommandInteractionEvent event) {
        return SlashCommandAcceptanceHelper.shouldAcceptIfActivePlayerOfGame(getActionId(), event);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String subcommandName = event.getInteraction().getSubcommandName();
        LeaderSubcommandData executedCommand = null;
        for (LeaderSubcommandData subcommand : subcommandData) {
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
    }

    protected String getActionDescription() {
        return "Leaders";
    }

    private Collection<LeaderSubcommandData> getSubcommands() {
        Collection<LeaderSubcommandData> subcommands = new HashSet<>();
        subcommands.add(new LeaderInfo());
        subcommands.add(new UnlockLeader());
        subcommands.add(new LockLeader());
        subcommands.add(new RefreshLeader());
        subcommands.add(new ExhaustLeader());
        subcommands.add(new PurgeLeader());
        subcommands.add(new ResetLeader());
        subcommands.add(new HeroPlay());
        subcommands.add(new HeroUnplay());

        return subcommands;
    }

    @Override
    public void registerCommands(CommandListUpdateAction commands) {
        commands.addCommands(
            Commands.slash(getActionId(), getActionDescription())
                .addSubcommands(getSubcommands()));
    }
}
