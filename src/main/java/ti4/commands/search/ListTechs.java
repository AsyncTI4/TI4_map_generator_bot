package ti4.commands.search;

import java.util.ArrayList;
import java.util.List;

import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.message.MessageHelper;
import ti4.model.TechnologyModel;

public class ListTechs extends SearchSubcommandData {

    public ListTechs() {
        super(Constants.SEARCH_TECHS, "List all techs the bot can use");
        addOptions(new OptionData(OptionType.STRING, Constants.SEARCH, "Searches the text and limits results to those containing this string.").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String searchString = event.getOption(Constants.SEARCH, null, OptionMapping::getAsString);

        if (Mapper.isValidTech(searchString)) {
            event.getChannel().sendMessageEmbeds(Mapper.getTech(searchString).getRepresentationEmbed(true, true)).queue();
            return;
        }

        List<MessageEmbed> messageEmbeds = new ArrayList<>();
        for (TechnologyModel techModel : Mapper.getTechs().values().stream().sorted(TechnologyModel.sortByTechRequirements).toList()) {
            MessageEmbed representationEmbed = techModel.getRepresentationEmbed(true, true);
            if (Helper.embedContainsSearchTerm(representationEmbed, searchString)) messageEmbeds.add(representationEmbed);
        }
        if (messageEmbeds.size() > 3) {
            String threadName = event.getFullCommandName() + (searchString == null ? "" : " search: " + searchString);
            MessageHelper.sendMessageEmbedsToThread(event.getChannel(), threadName, messageEmbeds);
        } else if (messageEmbeds.size() > 0) {
            event.getChannel().sendMessageEmbeds(messageEmbeds).queue();
        }
    }
}
