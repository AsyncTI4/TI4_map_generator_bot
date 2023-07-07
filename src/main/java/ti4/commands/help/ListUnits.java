package ti4.commands.help;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.collections4.ListUtils;

import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;

public class ListUnits extends HelpSubcommandData {

    public ListUnits() {
        super(Constants.LIST_UNITS, "List all units");
        addOptions(new OptionData(OptionType.STRING, Constants.SEARCH, "Searches the text and limits results to those containing this string."));
        // addOptions(new OptionData(OptionType.BOOLEAN, Constants.INCLUDE_ALIASES, "Set to true to also include common aliases, the ID, and source of the unit."));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String searchString = event.getOption(Constants.SEARCH, null, OptionMapping::getAsString);
        boolean includeAliases = event.getOption(Constants.INCLUDE_ALIASES, null, OptionMapping::getAsBoolean);
        List<MessageEmbed> messageEmbeds = new ArrayList<MessageEmbed>();

        for (UnitModel unitModel : Mapper.getUnits().values()) {
            MessageEmbed unitRepresentationEmbed = unitModel.getUnitRepresentationEmbed();
            if (searchString == null || unitRepresentationEmbed.getFooter().getText().toLowerCase().contains(searchString.toLowerCase())) {
                messageEmbeds.add(unitRepresentationEmbed);
            }
        }
        String threadName = "/help list_units" + (searchString == null ? "" : " search: " + searchString);
        MessageHelper.sendMessageEmbedsToThread(event.getChannel(), threadName, messageEmbeds);
    }
}
