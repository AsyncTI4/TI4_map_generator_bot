package ti4.commands.help;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import ti4.helpers.AliasHandler;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;

public class ListPlanets extends HelpSubcommandData {

    public ListPlanets() {
        super(Constants.LIST_PLANETS, "List all planets");
        addOptions(new OptionData(OptionType.STRING, Constants.SEARCH, "Searches the text and limits results to those containing this string."));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.INCLUDE_ALIASES, "True to also show the available aliases you can use"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Map<String, String> planetList= AliasHandler.getPlanetAliasEntryList();
        String searchString = event.getOption(Constants.SEARCH, null, OptionMapping::getAsString);
        boolean includeAliases = event.getOption(Constants.INCLUDE_ALIASES, false, OptionMapping::getAsBoolean);
        List<String> searchedList = planetList.entrySet().stream().map(e -> {
            if (includeAliases) {
                return "> " + e.getKey() + "=" + e.getValue();
            } else {
                return "> " + e.getKey();
            }
        })
        .filter(s -> searchString == null || s.toLowerCase().contains(searchString.toLowerCase()))
        .sorted().toList();

        String searchDescription = searchString == null ? "" : " search: " + searchString;
        String message = "**__Planet List__**\n" + String.join("\n", searchedList);

        if (searchedList.size() > 5) {
            String threadName = "/help list_planets" + searchDescription;
            MessageHelper.sendMessageToThread(event.getChannel(), threadName, message);
        } else if (searchedList.size() > 0) {
            event.getChannel().sendMessage(message).queue();
        }
    }
}
