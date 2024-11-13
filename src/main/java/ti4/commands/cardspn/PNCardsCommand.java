package ti4.commands.cardspn;

import java.util.Collection;
import java.util.List;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands.Command;
import ti4.commands.Subcommand;
import ti4.helpers.Constants;

public class PNCardsCommand implements Command {

    private final Collection<Subcommand> subcommands = List.of(
            new ShowPN(),
            new ShowAllPN(),
            new ShowPNToAll(),
            new PlayPN(),
            new SendPN(),
            new PurgePN(),
            new PNInfo(),
            new PNReset());

    @Override
    public String getActionId() {
        return Constants.CARDS_PN;
    }

    public String getActionDescription() {
        return "Promissory Notes";
    }

    @Override
    public boolean accept(SlashCommandInteractionEvent event) {
        return Command.super.accept(event) &&
                SlashCommandAcceptanceHelper.acceptIfPlayerInGame(event);
    }

    @Override
    public Collection<Subcommand> getSubcommands() {
        return subcommands;
    }
}
