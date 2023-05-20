package ti4.commands.help;

import java.util.HashMap;
import java.util.stream.Collectors;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.cardsac.ACInfo;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;
import ti4.model.ActionCardModel;

public class ListActionCards extends HelpSubcommandData {

    public ListActionCards() {
        super(Constants.LIST_ACTION_CARDS, "List all action cards the bot can use");
        addOptions(new OptionData(OptionType.STRING, Constants.SEARCH, "Searches the text and limits results to those containing this string."));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String searchString = event.getOption(Constants.SEARCH, null, OptionMapping::getAsString);
        HashMap<String, ActionCardModel> acList = Mapper.getActionCards();
        String message = "**__Action Card List__**\n" + acList.entrySet().stream()
            .map(e -> e.getKey() + " = " + ACInfo.getActionCardRepresentation(e.getKey()))
            .filter(s -> searchString == null ? true : s.toLowerCase().contains(searchString))
            .sorted()
            .collect(Collectors.joining("\n"));
        MessageHelper.sendMessageToThread(event.getChannel(), "Action Card List", message);
    }
}
