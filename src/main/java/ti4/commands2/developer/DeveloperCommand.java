package ti4.commands2.developer;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.AsyncTI4DiscordBot;
import ti4.commands2.CommandHelper;
import ti4.commands2.ParentCommand;
import ti4.commands2.Subcommand;
import ti4.helpers.Constants;

public class DeveloperCommand implements ParentCommand {

    private final Map<String, Subcommand> subcommands = Stream.of(
                    new SetGlobalSetting(),
                    new RunManualDataMigration(),
                    new GiveTheBotABreather(),
                    new ButtonProcessingStatistics())
            .collect(Collectors.toMap(Subcommand::getName, subcommand -> subcommand));

    @Override
    public String getName() {
        return Constants.DEVELOPER;
    }

    public String getDescription() {
        return "Developer";
    }

    @Override
    public boolean accept(SlashCommandInteractionEvent event) {
        return ParentCommand.super.accept(event) &&
            CommandHelper.acceptIfHasRoles(event, AsyncTI4DiscordBot.developerRoles);
    }

    @Override
    public Map<String, Subcommand> getSubcommands() {
        return subcommands;
    }
}
