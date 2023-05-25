package ti4.commands.help;

import java.util.HashMap;
import java.util.stream.Collectors;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;

public class ListPublicObjectives extends HelpSubcommandData {

    public ListPublicObjectives() {
        super(Constants.LIST_PUBLIC_OBJECTIVES, "List all public objectives the bot can use");
        addOptions(new OptionData(OptionType.STRING, Constants.SEARCH, "Searches the text and limits results to those containing this string."));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String searchString = event.getOption(Constants.SEARCH, null, OptionMapping::getAsString);
        HashMap<String, String> poList = Mapper.getPublicObjectivesStage1();
        poList.putAll(Mapper.getPublicObjectivesStage2());
        String message = "**__Public Objective List__**\n" + poList.entrySet().stream()
            .map(e -> e.getKey() + " = " + Mapper.getPublicObjective(e.getKey()))
            .filter(s -> searchString == null ? true : s.toLowerCase().contains(searchString))
            .sorted()
            .collect(Collectors.joining("\n"));
        MessageHelper.sendMessageToThread(event.getChannel(), "Public Objective List", message);
    }
}
