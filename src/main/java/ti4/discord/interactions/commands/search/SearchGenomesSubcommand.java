package ti4.discord.interactions.commands.search;

import java.util.Comparator;
import java.util.stream.Stream;
import ti4.helpers.Constants;
import ti4.image.Mapper;
import ti4.model.LeaderModel;
import ti4.model.Source.ComponentSource;

class SearchGenomesSubcommand extends SearchComponentModelSubcommand<LeaderModel> {

    SearchGenomesSubcommand() {
        super(
                Constants.SEARCH_GENOMES,
                "List all genomes (Twilight's Fall agents) the bot can use",
                s -> Mapper.isValidLeader(s) && Mapper.getLeader(s).isGenome(),
                Mapper::getLeader,
                SearchGenomesSubcommand::allGenomes,
                model -> model.getRepresentationEmbed(true, true, false, true, true),
                null);
    }

    private static Stream<LeaderModel> allGenomes() {
        Comparator<LeaderModel> byId = Comparator.comparing(LeaderModel::getId);
        return Stream.concat(
                Mapper.getDeck(Constants.TF_GENOME).getNewDeck().stream()
                        .map(Mapper::getLeader)
                        .sorted(byId),
                Mapper.getLeaders().values().stream()
                        .filter(model -> model.searchSource(ComponentSource.tk_nova_cup))
                        .sorted(byId));
    }
}
