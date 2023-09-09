package ti4.commands.help;

import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.message.MessageHelper;

public class ListRelics extends HelpSubcommandData {

    public ListRelics() {
        super(Constants.LIST_RELICS, "List all relics the bot can use");
        addOptions(new OptionData(OptionType.STRING, Constants.SEARCH, "Searches the text and limits results to those containing this string."));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String searchString = event.getOption(Constants.SEARCH, null, OptionMapping::getAsString);
        HashMap<String, String> relicList = Mapper.getRelics();
        List<String> searchedList = relicList.keySet().stream()
            .map(s -> s + " = " + Helper.getRelicRepresentation(s))
            .filter(s -> searchString == null || s.toLowerCase().contains(searchString.toLowerCase()))
            .sorted().toList();
        
        String searchDescription = searchString == null ? "" : " search: " + searchString;
        String message = "**__Relic List__**" + searchDescription + "\n" + String.join("\n", searchedList);
        if (searchedList.size() > 3) {
            String threadName = "/help list_relics" + searchDescription;
            MessageHelper.sendMessageToThread(event.getChannel(), threadName, message);
        } else if (searchedList.size() > 0) {
            event.getChannel().sendMessage(message).queue();
        }
    }
}
