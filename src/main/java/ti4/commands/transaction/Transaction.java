package ti4.commands.transaction;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands.GameStateCommand;
import ti4.helpers.Constants;
import ti4.helpers.TransactionHelper;

public class Transaction extends GameStateCommand {

    public Transaction() {
        super(true, true);
    }

    @Override
    public String getName() {
        return Constants.TRANSACTION;
    }

    public String getDescription() {
        return "Start a transaction with another player.";
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        TransactionHelper.transaction(getPlayer(), getGame());
    }
}
