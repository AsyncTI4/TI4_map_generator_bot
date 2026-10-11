package ti4.discord.interactions.commands.search;

import java.util.Comparator;
import ti4.helpers.Constants;
import ti4.image.Mapper;
import ti4.model.RelicModel;

class SearchRelicsSubcommand extends SearchComponentModelSubcommand<RelicModel> {

    SearchRelicsSubcommand() {
        super(
                Constants.SEARCH_RELICS,
                "List all relics the bot can use",
                Mapper::isValidRelic,
                Mapper::getRelic,
                valuesOf(() -> Mapper.getRelics().values()),
                model -> model.getRepresentationEmbed(true, true),
                Comparator.comparing(RelicModel::getName));
    }
}
