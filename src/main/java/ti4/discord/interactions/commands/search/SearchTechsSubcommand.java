package ti4.discord.interactions.commands.search;

import ti4.helpers.Constants;
import ti4.image.Mapper;
import ti4.model.TechnologyModel;

class SearchTechsSubcommand extends SearchComponentModelSubcommand<TechnologyModel> {

    SearchTechsSubcommand() {
        super(
                Constants.SEARCH_TECHS,
                "List all technologies the bot can use",
                Mapper::isValidTech,
                Mapper::getTech,
                valuesOf(() -> Mapper.getTechs().values()),
                model -> model.getRepresentationEmbed(true, true),
                TechnologyModel.sortByType.thenComparing(TechnologyModel.sortByTechRequirements));
    }
}
