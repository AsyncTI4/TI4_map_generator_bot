package ti4.commands.transaction;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.helpers.TransactionHelper;

public class TransactionStart extends GameStateSubcommand {

    public TransactionStart() {
        super(Constants.TRANSACTION_START, "Start a transaction with another player.", true, true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        TransactionHelper.transaction(getPlayer(), getGame());
    }
}
