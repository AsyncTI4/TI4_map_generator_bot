package ti4.commands2.search;

import java.util.Comparator;
import java.util.List;

import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.image.Mapper;
import ti4.image.TileHelper;
import ti4.helpers.Constants;
import ti4.model.PlanetModel;
import ti4.model.Source.ComponentSource;

class SearchPlanetsSubcommand extends SearchComponentModelSubcommand {

    public SearchPlanetsSubcommand() {
        super(Constants.SEARCH_PLANETS, "List all planets");
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.INCLUDE_ALIASES, "Set to true to also include common aliases, the ID, and source of the unit."));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String searchString = event.getOption(Constants.SEARCH, null, OptionMapping::getAsString);
        ComponentSource source = ComponentSource.fromString(event.getOption(Constants.SOURCE, null, OptionMapping::getAsString));
        boolean includeAliases = event.getOption(Constants.INCLUDE_ALIASES, false, OptionMapping::getAsBoolean);

        if (Mapper.isValidPlanet(searchString)) {
            event.getChannel().sendMessageEmbeds(Mapper.getPlanet(searchString).getRepresentationEmbed(includeAliases)).queue();
            return;
        }

        List<MessageEmbed> messageEmbeds = TileHelper.getAllPlanetModels().stream()
            .filter(model -> model.search(searchString, source))
            .sorted(Comparator.comparing(PlanetModel::getId))
            .map(model -> model.getRepresentationEmbed(true))
            .toList();
        SearchHelper.sendSearchEmbedsToEventChannel(event, messageEmbeds);
    }
}
