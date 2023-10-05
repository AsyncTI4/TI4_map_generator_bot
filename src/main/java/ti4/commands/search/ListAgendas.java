package ti4.commands.search;

import java.util.HashMap;
import java.util.List;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.message.MessageHelper;
import ti4.model.AgendaModel;

public class ListAgendas extends SearchSubcommandData {

    public ListAgendas() {
        super(Constants.SEARCH_AGENDAS, "List all agendas the bot can use");
        addOptions(new OptionData(OptionType.STRING, Constants.SEARCH, "Searches the text and limits results to those containing this string.").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String searchString = event.getOption(Constants.SEARCH, null, OptionMapping::getAsString);

        if (Mapper.isValidAgenda(searchString)) {
            event.getChannel().sendMessage(Helper.getAgendaRepresentation(searchString)).queue();
            return;
        }

        HashMap<String, AgendaModel> agendaList = Mapper.getAgendas();
        List<String> searchedList = agendaList.keySet().stream()
            .map(agendaKey -> agendaKey + " = " + Helper.getAgendaRepresentation(agendaKey))
            .filter(s -> searchString == null || s.toLowerCase().contains(searchString.toLowerCase()))
            .sorted().toList();

        String searchDescription = searchString == null ? "" : " search: " + searchString;
        String message = "**__Agenda List__**" + searchDescription + "\n" + String.join("\n", searchedList);
        if (searchedList.size() > 3) {
            String threadName = event.getFullCommandName() + searchDescription;
            MessageHelper.sendMessageToThread(event.getChannel(), threadName, message);
        } else if (searchedList.size() > 0) {
            event.getChannel().sendMessage(message).queue();
        }
    }
}
