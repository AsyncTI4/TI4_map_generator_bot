package ti4.commands2.agenda;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands2.CommandHelper;
import ti4.commands2.ParentCommand;
import ti4.commands2.Subcommand;
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
    public boolean accept(SlashCommandInteractionEvent event) {
        return ParentCommand.super.accept(event) &&
            CommandHelper.acceptIfPlayerInGameAndGameChannel(event);
    }

    @Override
    public Map<String, Subcommand> getSubcommands() {
        return subcommands;
    }
}
