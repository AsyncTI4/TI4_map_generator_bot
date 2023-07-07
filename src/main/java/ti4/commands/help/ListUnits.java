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
import ti4.model.UnitModel;

public class ListUnits extends HelpSubcommandData {

    public ListUnits() {
        super(Constants.LIST_UNITS, "List all units");
        addOptions(new OptionData(OptionType.STRING, Constants.SEARCH, "Searches the text and limits results to those containing this string."));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String searchString = event.getOption(Constants.SEARCH, null, OptionMapping::getAsString);
        List<MessageEmbed> messageEmbeds = new ArrayList<MessageEmbed>();

        for (UnitModel unitModel : Mapper.getUnits().values()) {
            MessageEmbed unitRepresentationEmbed = unitModel.getUnitRepresentationEmbed();
            if (searchString == null || unitRepresentationEmbed.getFooter().getText().toLowerCase().contains(searchString.toLowerCase())) {
                messageEmbeds.add(unitRepresentationEmbed);
            }
        }
        for (List<MessageEmbed> messageEmbeds_ : ListUtils.partition(messageEmbeds, 10)) { //max 10 embeds per message
            event.getChannel().sendMessageEmbeds(messageEmbeds_).queue();
        }
    }
}
