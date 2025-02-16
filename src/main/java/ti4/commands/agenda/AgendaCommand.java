package ti4.commands.agenda;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import ti4.commands.ParentCommand;
import ti4.commands.Subcommand;
import ti4.helpers.Constants;

public class AgendaCommand implements ParentCommand {

    private final Map<String, Subcommand> subcommands = Stream.of(
                    new DrawAgenda(),
                    new PutDrawnAgendaBackIntoDeck(),
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
                    new LawInfo())
            .collect(Collectors.toMap(Subcommand::getName, subcommand -> subcommand));


    @Override
    public String getName() {
        return Constants.AGENDA;
    }

    @Override
    public String getDescription() {
        return "Agenda handling";
    }

    @Override
    public Map<String, Subcommand> getSubcommands() {
        return subcommands;
    }
}
