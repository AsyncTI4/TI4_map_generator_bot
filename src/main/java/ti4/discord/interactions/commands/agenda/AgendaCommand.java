package ti4.discord.interactions.commands.agenda;

import ti4.discord.interactions.commands.AbstractParentCommand;
import ti4.helpers.Constants;

public class AgendaCommand extends AbstractParentCommand {

    public AgendaCommand() {
        super(
                Constants.AGENDA,
                "Agenda handling",
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
                new LawInfo());
    }
}
