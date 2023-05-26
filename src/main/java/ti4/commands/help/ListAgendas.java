package ti4.commands.help;

import java.util.HashMap;
import java.util.stream.Collectors;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.message.MessageHelper;
import ti4.model.AgendaModel;

public class ListAgendas extends HelpSubcommandData {

    public ListAgendas() {
        super(Constants.LIST_AGENDAS, "List all agendas the bot can use");
        addOptions(new OptionData(OptionType.STRING, Constants.SEARCH, "Searches the text and limits results to those containing this string."));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String searchString = event.getOption(Constants.SEARCH, null, OptionMapping::getAsString);
        HashMap<String, AgendaModel> agendaList = Mapper.getAgendas();
        String message = "**__Agenda List__**\n" + agendaList.keySet().stream()
            .map(agendaKey -> agendaKey + " = " + Helper.getAgendaRepresentation(agendaKey))
            .filter(s -> searchString == null ? true : s.toLowerCase().contains(searchString))
            .sorted()
            .collect(Collectors.joining("\n"));
        MessageHelper.sendMessageToThread(event.getChannel(), "Agenda List", message);
    }
}
