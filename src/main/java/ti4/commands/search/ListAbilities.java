package ti4.commands.search;

import java.util.HashMap;
import java.util.List;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.player.AbilityInfo;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;

public class ListAbilities extends SearchSubcommandData {

    public ListAbilities() {
        super(Constants.SEARCH_ABILITIES, "List all abilities");
        addOptions(new OptionData(OptionType.STRING, Constants.SEARCH, "Searches the text and limits results to those containing this string.").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String searchString = event.getOption(Constants.SEARCH, null, OptionMapping::getAsString);
        HashMap<String, String> abilityList = Mapper.getFactionAbilities();
        List<String> searchedList = abilityList.keySet().stream()
            .filter(s -> searchString == null || s.toLowerCase().contains(searchString))
            .map(AbilityInfo::getAbilityRepresentation)
            .sorted().toList();

        String searchDescription = searchString == null ? "" : " search: " + searchString;
        String message = "**__Ability List__**" + searchDescription + "\n" + String.join("\n", searchedList);
        if (searchedList.size() > 3) {
            String threadName = event.getFullCommandName() + searchDescription;
            MessageHelper.sendMessageToThread(event.getChannel(), threadName, message);
        } else if (searchedList.size() > 0) {
            event.getChannel().sendMessage(message).queue();
        }
    }
}
