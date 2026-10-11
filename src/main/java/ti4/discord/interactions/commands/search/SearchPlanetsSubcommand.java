package ti4.discord.interactions.commands.search;

import java.util.Comparator;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.helpers.Constants;
import ti4.image.Mapper;
import ti4.image.TileHelper;
import ti4.model.PlanetModel;

class SearchPlanetsSubcommand extends SearchComponentModelSubcommand<PlanetModel> {

    SearchPlanetsSubcommand() {
        super(
                Constants.SEARCH_PLANETS,
                "List all planets",
                Mapper::isValidPlanet,
                Mapper::getPlanet,
                valuesOf(TileHelper::getAllPlanetModels),
                model -> model.getRepresentationEmbed(true),
                Comparator.comparing(PlanetModel::getId));
        addOptions(new OptionData(
                OptionType.BOOLEAN,
                Constants.INCLUDE_ALIASES,
                "Set to true to also include common aliases, the ID, and source of the unit."));
    }

    @Override
    protected MessageEmbed exactMatchEmbed(PlanetModel model, SlashCommandInteractionEvent event) {
        return model.getRepresentationEmbed(includeAliases(event));
    }
}
