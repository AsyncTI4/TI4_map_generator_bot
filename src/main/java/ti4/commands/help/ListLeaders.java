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

public class ListLeaders extends HelpSubcommandData {

    public ListLeaders() {
        super(Constants.LIST_LEADERS, "List all leaders the bot can use");
        addOptions(new OptionData(OptionType.STRING, Constants.SEARCH, "Searches the text and limits results to those containing this string."));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String searchString = event.getOption(Constants.SEARCH, null, OptionMapping::getAsString);
        HashMap<String, String> leaderList = Mapper.getLeaderRepresentations();
        String message = "**__Leader List__**\n" + leaderList.entrySet().stream()
            .map(e -> "`" + e.getKey() + "`= " + Helper.getEmojiFromDiscord(e.getKey()) + e.getValue())
            .filter(s -> searchString == null ? true : s.toLowerCase().contains(searchString))
            .sorted()
            .collect(Collectors.joining("\n"));
        MessageHelper.sendMessageToThread(event.getChannel(), "Leader List", message);
    }
}
