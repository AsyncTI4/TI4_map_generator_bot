package ti4.commands.agenda;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.AgendaHelper;
import ti4.helpers.Constants;

class ShowDiscardedAgendas extends GameStateSubcommand {

    public ShowDiscardedAgendas() {
        super(Constants.SHOW_DISCARDED, "Show discarded Agendas", false, false);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        AgendaHelper.showDiscards(getGame(), event);
    }
}
