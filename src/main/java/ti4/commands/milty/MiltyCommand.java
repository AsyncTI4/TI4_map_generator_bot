package ti4.commands.milty;

import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import ti4.commands.CommandHelper;
import ti4.commands.ParentCommand;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.GameSaveLoadManager;
import ti4.map.UserGameContextManager;

public class MiltyCommand implements ParentCommand {

    private final Collection<MiltySubcommandData> subcommandData = getSubcommands();

    @Override
    public String getName() {
        return Constants.MILTY;
    }

    @Override
    public boolean accept(SlashCommandInteractionEvent event) {
        return CommandHelper.acceptIfPlayerInGame(event);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String subcommandName = event.getInteraction().getSubcommandName();
        for (MiltySubcommandData subcommand : subcommandData) {
            if (Objects.equals(subcommand.getName(), subcommandName)) {
                subcommand.preExecute(event);
                subcommand.execute(event);
                //executedCommand = subcommand;
                break;
            }
        }
        reply(event);
    }

    public static void reply(SlashCommandInteractionEvent event) {
        String userID = event.getUser().getId();
        Game game = CommandHelper.getGameName(event);
        GameSaveLoadManager.saveGame(game, event);
    }

    public String getDescription() {
        return "Milty draft";
    }

    private Collection<MiltySubcommandData> getSubcommands() {
        Collection<MiltySubcommandData> subcommands = new HashSet<>();
        subcommands.add(new DebugMilty());
        subcommands.add(new ForcePick());
        subcommands.add(new SetupMilty());
        subcommands.add(new StartMilty());
        subcommands.add(new ShowMilty());
        return subcommands;
    }

    @Override
    public void register(CommandListUpdateAction commands) {
        commands.addCommands(Commands.slash(getName(), getDescription()).addSubcommands(getSubcommands()));
    }
}
