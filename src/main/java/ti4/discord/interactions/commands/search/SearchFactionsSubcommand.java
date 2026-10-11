package ti4.discord.interactions.commands.search;

import java.util.Comparator;
import ti4.helpers.Constants;
import ti4.image.Mapper;
import ti4.model.FactionModel;

class SearchFactionsSubcommand extends SearchComponentModelSubcommand<FactionModel> {

    SearchFactionsSubcommand() {
        super(
                Constants.SEARCH_FACTIONS,
                "List all factions the bot can use",
                Mapper::isValidFaction,
                Mapper::getFaction,
                valuesOf(() -> Mapper.getFactionsValues()),
                model -> model.getRepresentationEmbed(true, false),
                Comparator.comparing(FactionModel::getAlias));
    }
}
