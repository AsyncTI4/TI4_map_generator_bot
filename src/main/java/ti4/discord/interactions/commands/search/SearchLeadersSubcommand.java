package ti4.discord.interactions.commands.search;

import java.util.Comparator;
import ti4.helpers.Constants;
import ti4.image.Mapper;
import ti4.model.LeaderModel;

class SearchLeadersSubcommand extends SearchComponentModelSubcommand<LeaderModel> {

    SearchLeadersSubcommand() {
        super(
                Constants.SEARCH_LEADERS,
                "List all leaders the bot can use",
                Mapper::isValidLeader,
                Mapper::getLeader,
                valuesOf(() -> Mapper.getLeaders().values()),
                model -> model.getRepresentationEmbed(true, true, true, true),
                Comparator.comparing(LeaderModel::getId));
    }
}
