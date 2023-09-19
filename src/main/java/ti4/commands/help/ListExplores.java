package ti4.commands.help;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;

public class ListExplores extends HelpSubcommandData {

    public ListExplores() {
        super(Constants.LIST_EXPLORES, "List all explore cards the bot can use");
        addOptions(new OptionData(OptionType.STRING, Constants.SEARCH, "Searches the text and limits results to those containing this string.").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String searchString = event.getOption(Constants.SEARCH, null, OptionMapping::getAsString);
        HashMap<String, String> exploreList = Mapper.getExplores();
        List<String> searchedList = exploreList.entrySet().stream()
            .map(e -> Map.entry(e.getKey() + " = **" + e.getValue() + "**", Mapper.getExplore(e.getKey())))
            .map(e -> e.getKey() + "\n> " + e.getValue())
            .filter(s -> searchString == null || s.toLowerCase().contains(searchString.toLowerCase()))
            .sorted().toList();
        
        String searchDescription = searchString == null ? "" : " search: " + searchString;
        String message = "**__Explore List__**" + searchDescription + "\n" + String.join("\n", searchedList);
        if (searchedList.size() > 5) {
            String threadName = "/help list_explores" + searchDescription;
            MessageHelper.sendMessageToThread(event.getChannel(), threadName, message);
        } else if (searchedList.size() > 0) {
            event.getChannel().sendMessage(message).queue();
        }
    }
}
