package ti4.discord.interactions.commands.transaction;

import ti4.discord.interactions.commands.AbstractParentCommand;
import ti4.helpers.Constants;

public class TransactionCommand extends AbstractParentCommand {

    public TransactionCommand() {
        super(Constants.TRANSACTION, "transaction", new TransactionStart(), new ChangeDebtIcon());
    }
}
