package ti4.commands.search;

import java.util.List;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import ti4.helpers.Constants;
import ti4.image.Mapper;
import ti4.model.Source.ComponentSource;

class SearchCalamitiesSubcommand extends SearchComponentModelSubcommand {

    public SearchCalamitiesSubcommand() {
        super(Constants.SEARCH_CALAMITIES, "List all calamities the bot can use");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String searchString = event.getOption(Constants.SEARCH, null, OptionMapping::getAsString);
        ComponentSource source =
                ComponentSource.fromString(event.getOption(Constants.SOURCE, null, OptionMapping::getAsString));

        if (Mapper.isValidCalamity(searchString)) {
            event.getChannel()
                    .sendMessageEmbeds(Mapper.getCalamity(searchString).getRepresentationEmbed(true, true))
                    .queue();
            return;
        }

        List<MessageEmbed> messageEmbeds = Mapper.getCalamities().values().stream()
                .filter(model -> model.search(searchString, source))
                .map(model -> model.getRepresentationEmbed(true, true))
                .toList();
        SearchHelper.sendSearchEmbedsToEventChannel(event, messageEmbeds);
    }
}
