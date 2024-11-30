package ti4.commands2.agenda;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands2.GameStateSubcommand;
import ti4.helpers.AgendaHelper;
import ti4.helpers.Constants;

class ListVoteCount extends GameStateSubcommand {

    public ListVoteCount() {
        super(Constants.VOTE_COUNT, "List Vote count for agenda", false, false);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        AgendaHelper.listVoteCount(event, getGame());
    }
}
