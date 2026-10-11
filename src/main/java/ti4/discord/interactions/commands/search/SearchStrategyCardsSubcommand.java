package ti4.discord.interactions.commands.search;

import java.util.Comparator;
import ti4.helpers.Constants;
import ti4.image.Mapper;
import ti4.model.StrategyCardModel;

class SearchStrategyCardsSubcommand extends SearchComponentModelSubcommand<StrategyCardModel> {

    SearchStrategyCardsSubcommand() {
        super(
                Constants.SEARCH_STRATEGY_CARDS,
                "List all strategy cards the bot can use",
                Mapper::isValidStrategyCard,
                Mapper::getStrategyCard,
                valuesOf(() -> Mapper.getStrategyCards().values()),
                model -> model.getRepresentationEmbed(true),
                Comparator.comparing(StrategyCardModel::getInitiative));
    }
}
