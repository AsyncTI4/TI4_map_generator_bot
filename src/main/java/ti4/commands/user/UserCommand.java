package ti4.commands.user;

import java.util.Collection;
import java.util.List;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands.Command;
import ti4.commands.Subcommand;
import ti4.helpers.Constants;
import ti4.helpers.SlashCommandAcceptanceHelper;

public class UserCommand implements Command {

    private final Collection<Subcommand> subcommands = List.of(
            new ShowUserSettings(),
            new SetPreferredColourList(),
            new SetPersonalPingInterval());

    @Override
    public String getActionId() {
        return Constants.USER;
    }

    @Override
    public String getActionDescription() {
        return "User";
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
