package ti4.commands.agenda;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import ti4.commands2.GameStateSubcommand;
import ti4.helpers.AgendaHelper;
import ti4.helpers.Constants;

class RevealAgenda extends GameStateSubcommand {

    public RevealAgenda() {
        super(Constants.REVEAL, "Reveal top Agenda from deck", true, false);
        addOption(OptionType.BOOLEAN, Constants.REVEAL_FROM_BOTTOM,
            "Reveal the agenda from the bottom of the deck instead of the top");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        boolean revealFromBottom = event.getOption(Constants.REVEAL_FROM_BOTTOM, false, OptionMapping::getAsBoolean);
        AgendaHelper.revealAgenda(event, revealFromBottom, getGame(), event.getChannel());
    }
}
