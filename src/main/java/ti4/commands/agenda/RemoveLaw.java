package ti4.commands.agenda;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;

class RemoveLaw extends GameStateSubcommand {

    public RemoveLaw() {
        super(Constants.REMOVE_LAW, "Remove Law", true, false);
        addOptions(new OptionData(OptionType.INTEGER, Constants.AGENDA_ID, "Agenda ID, which is found between ()").setRequired(true).setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        int agendaId = event.getOption(Constants.AGENDA_ID).getAsInt();
        boolean success = getGame().removeLaw(agendaId);
        if (success) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Law removed.");
        } else {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Law ID not found.");
        }
    }
}
