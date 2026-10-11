package ti4.discord.interactions.commands.search;

import ti4.helpers.Constants;
import ti4.image.Mapper;
import ti4.model.BreakthroughModel;

class SearchBreakthroughs extends SearchComponentModelSubcommand<BreakthroughModel> {

    SearchBreakthroughs() {
        super(
                Constants.SEARCH_BREAKTHROUGHS,
                "List all breakthorughs the bot can use",
                Mapper::isValidBreakthrough,
                Mapper::getBreakthrough,
                valuesOf(() -> Mapper.getBreakthroughs().values()),
                model -> model.getRepresentationEmbed(true),
                null);
    }
}
