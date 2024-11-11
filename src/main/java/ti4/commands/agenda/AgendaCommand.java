package ti4.commands.agenda;

import java.util.Collection;
import java.util.List;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands.Command;
import ti4.commands.Subcommand;
import ti4.helpers.Constants;
import ti4.helpers.SlashCommandAcceptanceHelper;

public class AgendaCommand implements Command {

    private final Collection<Subcommand> subcommands = List.of(
            new DrawAgenda(),
            new PutAgendaTop(),
            new PutAgendaBottom(),
            new LookAtAgenda(),
            new RevealAgenda(),
            new RevealSpecificAgenda(),
            new AddLaw(),
            new RemoveLaw(),
            new ReviseLaw(),
            new ShowDiscardedAgendas(),
            new ListVoteCount(),
            new ShuffleAgendas(),
            new ResetAgendas(),
            new Cleanup(),
            new ExhaustSC(),
            new AddControlToken(),
            new ResetDrawStateAgendas(),
            new PutDiscardBackIntoDeckAgendas(),
            new LawInfo());


    @Override
    public String getActionId() {
        return Constants.AGENDA;
    }

    @Override
    public String getActionDescription() {
        return "Agenda handling";
    }

    @Override
    public boolean accept(SlashCommandInteractionEvent event) {
        return Command.super.accept(event) &&
                SlashCommandAcceptanceHelper.acceptIfPlayerInGame(event);
    }

    @Override
    public Collection<Subcommand> getSubcommands() {
        return subcommands;
    }
}
