package ti4.discord.interactions.commands.search;

import java.util.Comparator;
import ti4.helpers.Constants;
import ti4.image.Mapper;
import ti4.model.ExploreModel;

class SearchExploresSubcommand extends SearchComponentModelSubcommand<ExploreModel> {

    SearchExploresSubcommand() {
        super(
                Constants.SEARCH_EXPLORES,
                "List all exploration cards the bot can use",
                Mapper::isValidExplore,
                Mapper::getExplore,
                valuesOf(() -> Mapper.getExplores().values()),
                model -> model.getRepresentationEmbed(true, true),
                Comparator.comparing(ExploreModel::getName));
    }
}
