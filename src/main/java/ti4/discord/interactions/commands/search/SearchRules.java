package ti4.discord.interactions.commands.search;

import ti4.helpers.Constants;
import ti4.image.Mapper;
import ti4.model.RuleModel;

class SearchRules extends SearchComponentModelSubcommand<RuleModel> {

    SearchRules() {
        super(
                Constants.SEARCH_RULES,
                "List various rules sections from the LRR or other homebrew sources.",
                Mapper::isValidRule,
                Mapper::getRule,
                valuesOf(() -> Mapper.getRules().values()),
                RuleModel::getRepresentationEmbed,
                null);
    }
}
