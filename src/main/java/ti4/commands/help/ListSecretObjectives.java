package ti4.commands.help;

import java.util.HashMap;
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
        String message = "**__Secret Objective List__**\n" + soList.entrySet().stream()
            .map(e -> e.getKey() + " = " + SOInfo.getSecretObjectiveRepresentation(e.getKey()))
            .filter(s -> searchString == null ? true : s.toLowerCase().contains(searchString))
            .filter(s -> !s.contains("_pbd100"))
            .sorted()
            .collect(Collectors.joining("\n"));
        MessageHelper.sendMessageToThread(event.getChannel(), "Secret Objective List", message);
    }
}
