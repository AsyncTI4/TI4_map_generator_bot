package ti4.commands.help;

import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.cardsso.SOInfo;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;
import ti4.model.SecretObjectiveModel;

public class ListSecretObjectives extends HelpSubcommandData {

    public ListSecretObjectives() {
        super(Constants.LIST_SECRET_OBJECTIVES, "List all secret objectives the bot can use");
        addOptions(new OptionData(OptionType.STRING, Constants.SEARCH, "Searches the text and limits results to those containing this string."));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String searchString = event.getOption(Constants.SEARCH, null, OptionMapping::getAsString);
        HashMap<String, SecretObjectiveModel> soList = Mapper.getSecretObjectives();
        List<String> searchedList = soList.entrySet().stream()
            .map(e -> e.getKey() + " = " + SOInfo.getSecretObjectiveRepresentation(e.getKey()))
            .filter(s -> searchString == null ? true : s.toLowerCase().contains(searchString))
            .sorted().toList();

        String searchDescription = searchString == null ? "" : " search: " + searchString;
        String message = "**__Secret Objective List__**" + searchDescription + "\n" + searchedList.stream().collect(Collectors.joining("\n"));
        if (searchedList.size() > 5) {
            String threadName = "/help list_secret_objectives" + searchDescription;
            MessageHelper.sendMessageToThread(event.getChannel(), threadName, message);
        } else if (searchedList.size() > 0) {
            event.getChannel().sendMessage(message).queue();
        }
    }
}
