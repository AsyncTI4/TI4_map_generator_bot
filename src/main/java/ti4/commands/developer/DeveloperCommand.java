package ti4.commands.developer;

import java.util.Collection;
import java.util.List;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.AsyncTI4DiscordBot;
import ti4.commands.Command;
import ti4.commands.Subcommand;
import ti4.helpers.Constants;

public class DeveloperCommand implements Command {

    private final Collection<Subcommand> subcommands = List.of(
            new SetGlobalSetting(),
            new RunManualDataMigration());

    @Override
    public String getActionId() {
        return Constants.DEVELOPER;
    }

    public String getActionDescription() {
        return "Developer";
    }

    @Override
    public boolean accept(SlashCommandInteractionEvent event) {
        return Command.super.accept(event) &&
                SlashCommandAcceptanceHelper.acceptIfHasRoles(event, AsyncTI4DiscordBot.developerRoles);
    }

    @Override
    public Collection<Subcommand> getSubcommands() {
        return subcommands;
    }
}
