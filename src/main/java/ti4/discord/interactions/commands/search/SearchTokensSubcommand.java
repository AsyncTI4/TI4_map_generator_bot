package ti4.discord.interactions.commands.search;

import java.util.Comparator;
import ti4.helpers.Constants;
import ti4.image.Mapper;
import ti4.model.TokenModel;

class SearchTokensSubcommand extends SearchComponentModelSubcommand<TokenModel> {

    SearchTokensSubcommand() {
        super(
                Constants.SEARCH_TOKENS,
                "List all tokens the bot can use",
                Mapper::isValidToken,
                Mapper::getToken,
                valuesOf(() -> Mapper.getTokens().values()),
                TokenModel::getRepresentationEmbed,
                Comparator.comparing(TokenModel::getSource));
    }
}
