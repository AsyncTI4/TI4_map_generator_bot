package ti4.commands.agenda;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.helpers.Constants;
import ti4.map.Map;
import ti4.message.MessageHelper;

public class PutAgendaTop extends AgendaSubcommandData {
    public PutAgendaTop() {
        super(Constants.PUT_TOP, "Put Agenda top");
        addOptions(new OptionData(OptionType.INTEGER, Constants.AGENDA_ID, "Agenda ID that is sent between ()").setRequired(true));
    }

    
    public void putTop(GenericInteractionCreateEvent event, int agendaID, Map activeMap) {
        boolean success = activeMap.putAgendaTop(agendaID);
        if (success) {
            MessageHelper.sendMessageToChannel(activeMap.getActionsChannel(), "Agenda put at top");
        } else {
            MessageHelper.sendMessageToChannel(activeMap.getActionsChannel(), "No Agenda ID found");
        }
    }
    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Map activeMap = getActiveMap();
        OptionMapping option = event.getOption(Constants.AGENDA_ID);
        if (option == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "No Agenda ID defined");
            return;
        }
        putTop(event, option.getAsInt(),activeMap);
    }
}
