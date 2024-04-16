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
import ti4.message.MessageHelper;
import ti4.model.ActionCardModel;
import ti4.model.Source.ComponentSource;

public class ListActionCards extends SearchSubcommandData {

    public ListActionCards() {
        super(Constants.SEARCH_ACTION_CARDS, "List all action cards the bot can use");
        addOptions(new OptionData(OptionType.STRING, Constants.SEARCH, "Searches the text and limits results to those containing this string.").setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.SOURCE, "Limit results to a specific source.").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String searchString = event.getOption(Constants.SEARCH, null, OptionMapping::getAsString);
        ComponentSource source = ComponentSource.fromString(event.getOption(Constants.SOURCE, null, OptionMapping::getAsString));

        if (Mapper.isValidActionCard(searchString)) {
            event.getChannel().sendMessageEmbeds(Mapper.getActionCard(searchString).getRepresentationEmbed(true, true)).queue();
            return;
        }

        List<MessageEmbed> messageEmbeds = new ArrayList<>();

        for (ActionCardModel model : Mapper.getActionCards().values()) {
            MessageEmbed representationEmbed = model.getRepresentationEmbed(true, true);
            if (model.search(searchString, source)) messageEmbeds.add(representationEmbed);
        }
        if (messageEmbeds.size() > 3) {
            String threadName = event.getCommandString();
            MessageHelper.sendMessageEmbedsToThread(event.getChannel(), threadName, messageEmbeds);
        } else if (messageEmbeds.size() > 0) {
            event.getChannel().sendMessageEmbeds(messageEmbeds).queue();
        }
    }
}
