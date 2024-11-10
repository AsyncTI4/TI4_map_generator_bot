package ti4.commands.agenda;

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
import ti4.map.GameManager;
import ti4.map.GameSaveLoadManager;
import ti4.message.MessageHelper;

public class AgendaCommand implements Command {

    private final Collection<AgendaSubcommandData> subcommandData = getSubcommands();

    @Override
    public String getActionID() {
        return Constants.AGENDA;
    }

    @Override
    public boolean accept(SlashCommandInteractionEvent event) {
        return SlashCommandAcceptanceHelper.shouldAcceptIfIsAdminOrIsPartOfGame(getActionID(), event);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String subcommandName = event.getInteraction().getSubcommandName();
        AgendaSubcommandData executedCommand = null;
        for (AgendaSubcommandData subcommand : subcommandData) {
            if (Objects.equals(subcommand.getName(), subcommandName)) {
                subcommand.preExecute(event);
                subcommand.execute(event);
                executedCommand = subcommand;
                break;
            }
        }

        Game game = UserGameContextManager.getContextGame(event.getUser().getId());
        if (game != null) {
            GameSaveLoadManager.saveGame(game, event);
        }
        if (executedCommand != null) {
            // MessageHelper.replyToMessage(event, "Executed action: " + executedCommand.getActionID());
        } else {
            MessageHelper.replyToMessage(event, "No Action executed");
        }
    }

    protected String getActionDescription() {
        return "Agenda handling";
    }

    private Collection<AgendaSubcommandData> getSubcommands() {
        Collection<AgendaSubcommandData> subcommands = new HashSet<>();
        subcommands.add(new DrawAgenda());
        subcommands.add(new PutAgendaTop());
        subcommands.add(new PutAgendaBottom());
        subcommands.add(new LookAtTopAgenda());
        subcommands.add(new LookAtBottomAgenda());
        subcommands.add(new RevealAgenda());
        subcommands.add(new RevealSpecificAgenda());
        subcommands.add(new AddLaw());
        subcommands.add(new RemoveLaw());
        subcommands.add(new ReviseLaw());
        subcommands.add(new ShowDiscardedAgendas());
        subcommands.add(new ListVoteCount());
        subcommands.add(new ShuffleAgendas());
        subcommands.add(new ResetAgendas());
        subcommands.add(new Cleanup());
        subcommands.add(new ExhaustSC());
        subcommands.add(new AddControlToken());
        subcommands.add(new ResetDrawStateAgendas());
        subcommands.add(new PutDiscardBackIntoDeckAgendas());
        subcommands.add(new LawInfo());
        return subcommands;
    }

    @Override
    public void registerCommands(CommandListUpdateAction commands) {
        commands.addCommands(
            Commands.slash(getActionID(), getActionDescription())
                .addSubcommands(getSubcommands()));
    }
}
