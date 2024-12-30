package ti4.commands2.agenda;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands2.GameStateSubcommand;
import ti4.helpers.AgendaHelper;
import ti4.helpers.Constants;

// TODO: maybe combine PutTop and PutBottom?
class PutDrawnAgendaBackIntoDeck extends GameStateSubcommand {

    public PutDrawnAgendaBackIntoDeck() {
        super(Constants.PUT_IN_DECK, "Put a drawn agenda back into the deck", true, false);
        addOptions(new OptionData(OptionType.INTEGER, Constants.AGENDA_ID, "Agenda ID, which is found between ()").setRequired(true));
        addOption(OptionType.BOOLEAN, Constants.PUT_ON_BOTTOM, "Put the agenda on the bottom of the deck");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        int agendaId = event.getOption(Constants.AGENDA_ID).getAsInt();
        boolean onBottom = event.getOption(Constants.PUT_ON_BOTTOM, false, OptionMapping::getAsBoolean);
        if (onBottom) {
            AgendaHelper.putBottom(agendaId, getGame());
        } else {
            AgendaHelper.putTop(agendaId, getGame());
        }
    }
}
