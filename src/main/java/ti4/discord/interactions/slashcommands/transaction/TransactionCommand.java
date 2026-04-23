package ti4.discord.interactions.slashcommands.transaction;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import ti4.discord.interactions.slashcommands.ParentCommand;
import ti4.discord.interactions.slashcommands.Subcommand;
import ti4.helpers.Constants;

public class TransactionCommand implements ParentCommand {

    private final Map<String, Subcommand> subcommands = Stream.of(new TransactionStart(), new ChangeDebtIcon())
            .collect(Collectors.toMap(Subcommand::getName, subcommand -> subcommand));

    @Override
    public String getName() {
        return Constants.TRANSACTION;
    }

    @Override
    public String getDescription() {
        return "transaction";
    }

    @Override
    public Map<String, Subcommand> getSubcommands() {
        return subcommands;
    }
}
