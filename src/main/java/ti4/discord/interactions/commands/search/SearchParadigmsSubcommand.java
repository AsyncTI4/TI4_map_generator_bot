package ti4.discord.interactions.commands.search;

import java.util.Comparator;
import ti4.helpers.Constants;
import ti4.image.Mapper;
import ti4.model.LeaderModel;

class SearchParadigmsSubcommand extends SearchComponentModelSubcommand<LeaderModel> {

    SearchParadigmsSubcommand() {
        super(
                Constants.SEARCH_PARADIGMS,
                "List all paradigms (Twilight's Fall heroes) the bot can use",
                s -> Mapper.isValidLeader(s) && Mapper.getLeader(s).isParadigm(),
                Mapper::getLeader,
                () -> Mapper.getDeck(Constants.TF_PARADIGM).getNewDeck().stream()
                        .map(Mapper::getLeader),
                model -> model.getRepresentationEmbed(true, true, false, true, true),
                Comparator.comparing(LeaderModel::getId));
    }
}
