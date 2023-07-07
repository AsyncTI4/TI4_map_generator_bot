package ti4.commands.help;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.collections4.ListUtils;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.model.UnitModel;

public class ListUnits extends HelpSubcommandData {

    public ListUnits() {
        super(Constants.LIST_UNITS, "List all units");
        addOptions(new OptionData(OptionType.STRING, Constants.SEARCH, "Searches the text and limits results to those containing this string."));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        // String searchString = event.getOption(Constants.SEARCH, null, OptionMapping::getAsString);

        // Map<String, UnitModel> unitList = Mapper.getUnits();
        // String message = "**__Unit List__**\n" + unitList.entrySet().stream()
        //     .map(e -> "`" + e.getKey() + "`= " + getUnitRepresentation(e.getKey()))
        //     .filter(s -> searchString == null ? true : s.toLowerCase().contains(searchString))
        //     .sorted()
        //     .collect(Collectors.joining("\n"));

        // MessageHelper.sendMessageToThread(event.getChannel(), "Unit List", message);

        List<MessageEmbed> messageEmbeds = new ArrayList<MessageEmbed>();

        for (UnitModel unitModel : Mapper.getUnits().values()) {
            messageEmbeds.add(unitModel.getUnitRepresentationEmbed());
        }
        for (List<MessageEmbed> messageEmbeds_ : ListUtils.partition(messageEmbeds, 10)) {
            event.getChannel().sendMessageEmbeds(messageEmbeds_).queue();
        }
    }
}
