package ti4.commands.cardspn;

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

public class PNCardsCommand implements Command {

    private final Collection<PNCardsSubcommandData> subcommandData = getSubcommands();

    @Override
    public String getActionId() {
        return Constants.CARDS_PN;
    }

    @Override
    public boolean accept(SlashCommandInteractionEvent event) {
        return SlashCommandAcceptanceHelper.shouldAcceptIfIsAdminOrIsPartOfGame(getActionId(), event);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String subcommandName = event.getInteraction().getSubcommandName();
        PNCardsSubcommandData subCommandExecuted = null;
        for (PNCardsSubcommandData subcommand : subcommandData) {
            if (Objects.equals(subcommand.getName(), subcommandName)) {
                subcommand.preExecute(event);
                subcommand.execute(event);
                subCommandExecuted = subcommand;
                break;
            }
        }
        if (subCommandExecuted == null) {
            reply(event);
        } else {
            subCommandExecuted.reply(event);
        }
    }

    public static void reply(SlashCommandInteractionEvent event) {
        String userID = event.getUser().getId();
        Game game = UserGameContextManager.getContextGame(userID);
        GameSaveLoadManager.saveGame(game, event);
        // new GenerateMap().saveImage(activeMap, event);
    }

    protected String getActionDescription() {
        return "Promissory Notes";
    }

    private Collection<PNCardsSubcommandData> getSubcommands() {
        Collection<PNCardsSubcommandData> subcommands = new HashSet<>();
        subcommands.add(new ShowPN());
        subcommands.add(new ShowAllPN());
        subcommands.add(new ShowPNToAll());
        subcommands.add(new PlayPN());
        subcommands.add(new SendPN());
        subcommands.add(new PurgePN());
        subcommands.add(new PNInfo());
        subcommands.add(new PNReset());
        return subcommands;
    }

    @Override
    public void registerCommands(CommandListUpdateAction commands) {
        commands.addCommands(
            Commands.slash(getActionId(), getActionDescription())
                .addSubcommands(getSubcommands()));
    }
}
