package ti4.commands2.cardspn;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import ti4.commands2.ParentCommand;
import ti4.commands2.Subcommand;
import ti4.helpers.Constants;

public class PNCardsCommand implements ParentCommand {

    private final Map<String, Subcommand> subcommands = Stream.of(
                    new ShowPN(),
                    new ShowAllPN(),
                    new ShowPNToAll(),
                    new PlayPN(),
                    new SendPN(),
                    new PurgePN(),
                    new PNInfo(),
                    new PNReset())
            .collect(Collectors.toMap(Subcommand::getName, subcommand -> subcommand));

    @Override
    public String getName() {
        return Constants.CARDS_PN;
    }

    public String getDescription() {
        return "Promissory Notes";
    }

    @Override
    public Map<String, Subcommand> getSubcommands() {
        return subcommands;
    }
}
