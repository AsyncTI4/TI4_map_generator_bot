package ti4.commands2.search;

import java.util.Comparator;
import java.util.List;

import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.model.ExploreModel;
import ti4.model.Source.ComponentSource;

class SearchExploresSubcommand extends SearchComponentModelSubcommand {

    public SearchExploresSubcommand() {
        super(Constants.SEARCH_EXPLORES, "List all explore cards the bot can use");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String searchString = event.getOption(Constants.SEARCH, null, OptionMapping::getAsString);
        ComponentSource source = ComponentSource.fromString(event.getOption(Constants.SOURCE, null, OptionMapping::getAsString));

        if (Mapper.isValidExplore(searchString)) {
            event.getChannel().sendMessageEmbeds(Mapper.getExplore(searchString).getRepresentationEmbed(true, true)).queue();
            return;
        }

        List<MessageEmbed> messageEmbeds = Mapper.getExplores().values().stream()
            .filter(model -> model.search(searchString, source))
            .sorted(Comparator.comparing(ExploreModel::getName))
            .map(model -> model.getRepresentationEmbed(true, true))
            .toList();
        SearchHelper.sendSearchEmbedsToEventChannel(event, messageEmbeds);
    }
}
