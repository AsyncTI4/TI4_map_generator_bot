package ti4.commands.transaction;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import ti4.commands.ParentCommand;
import ti4.commands.Subcommand;
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
