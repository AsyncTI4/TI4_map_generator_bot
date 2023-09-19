package ti4.commands.help;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;
import ti4.model.RelicModel;
import ti4.model.UnitModel;

public class ListRelics extends HelpSubcommandData {

    public ListRelics() {
        super(Constants.LIST_RELICS, "List all relics the bot can use");
        addOptions(new OptionData(OptionType.STRING, Constants.SEARCH, "Searches the text and limits results to those containing this string.").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String searchString = event.getOption(Constants.SEARCH, null, OptionMapping::getAsString);
        List<MessageEmbed> messageEmbeds = new ArrayList<>();
        for (RelicModel relicModel : Mapper.getRelics().values().stream().sorted(Comparator.comparing(RelicModel::getName)).toList()) {
            MessageEmbed representationEmbed = relicModel.getRepresentationEmbed();
            if (searchString == null || representationEmbed.getTitle().toLowerCase().contains(searchString.toLowerCase())) {
                messageEmbeds.add(representationEmbed);
            }
        }
        if (messageEmbeds.size() > 3) {
            String threadName = "/help list_relics" + (searchString == null ? "" : " search: " + searchString);
            MessageHelper.sendMessageEmbedsToThread(event.getChannel(), threadName, messageEmbeds);
        } else if (messageEmbeds.size() > 0) {
            event.getChannel().sendMessageEmbeds(messageEmbeds).queue();
        }
    }
}
