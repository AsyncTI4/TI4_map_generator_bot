package ti4.commands.help;

import java.util.HashMap;
import java.util.List;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;
import ti4.model.PromissoryNoteModel;

public class ListPromissoryNotes extends HelpSubcommandData {

    public ListPromissoryNotes() {
        super(Constants.LIST_PROMISSORY_NOTES, "List all promissory notes the bot can use");
        addOptions(new OptionData(OptionType.STRING, Constants.SEARCH, "Searches the text and limits results to those containing this string.").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String searchString = event.getOption(Constants.SEARCH, null, OptionMapping::getAsString);
        HashMap<String, PromissoryNoteModel> promList = Mapper.getPromissoryNotes();
        List<String> searchedList = promList.entrySet().stream()
            .map(e -> e.getKey() + " = **" + e.getValue().getName() + "**\n> " + e.getValue().getText())
            .filter(s -> searchString == null || s.toLowerCase().contains(searchString.toLowerCase()))
            .sorted().toList();
        
        String searchDescription = searchString == null ? "" : " search: " + searchString;
        String message = "**__Promissory Note List__**" + searchDescription + "\n" + String.join("\n", searchedList);
        if (searchedList.size() > 5) {
            String threadName = "/help list_promissory_notes" + searchDescription;
            MessageHelper.sendMessageToThread(event.getChannel(), threadName, message);
        } else if (searchedList.size() > 0) {
            event.getChannel().sendMessage(message).queue();
        }
    }
}
