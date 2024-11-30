package ti4.commands2.agenda;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands2.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;

class PutDiscardBackIntoDeckAgendas extends GameStateSubcommand {

    public PutDiscardBackIntoDeckAgendas() {
        super(Constants.PUT_DISCARD_BACK_INTO_DECK, "Put agenda back into deck from discard", true, false);
        addOptions(new OptionData(OptionType.INTEGER, Constants.AGENDA_ID, "Agenda ID that is sent between ()").setRequired(true).setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.SHUFFLE_AGENDAS, "Enter YES to shuffle, otherwise NO to put on top").setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        OptionMapping agendaIdOption = event.getOption(Constants.AGENDA_ID);
        if (agendaIdOption == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "No Agenda ID defined");
            return;
        }
        OptionMapping shuffleAgendasOption = event.getOption(Constants.SHUFFLE_AGENDAS);
        boolean success = false;
        if (shuffleAgendasOption != null) {
            if ("YES".equalsIgnoreCase(shuffleAgendasOption.getAsString())) {
                success = getGame().shuffleAgendaBackIntoDeck(agendaIdOption.getAsInt());
            } else {
                success = getGame().putAgendaBackIntoDeckOnTop(agendaIdOption.getAsInt());
            }
        }

        if (success) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Agenda put back into deck");
        } else {
            MessageHelper.sendMessageToChannel(event.getChannel(), "No Agenda found");
        }
    }
}
