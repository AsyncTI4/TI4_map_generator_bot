package ti4.commands2.admin;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.AsyncTI4DiscordBot;
import ti4.commands2.CommandHelper;
import ti4.commands2.ParentCommand;
import ti4.commands2.Subcommand;
import ti4.helpers.Constants;

public class AdminCommand implements ParentCommand {

    private final Map<String, Subcommand> subcommands = Stream.of(
                    new DeleteGame(),
                    new ResetEmojiCache(),
                    new ReloadMap(),
                    new ReloadMapperObjects(),
                    new RestoreGame(),
                    new CardsInfoForPlayer(),
                    // new UploadStatistics(),
                    new UpdateThreadArchiveTime())
            .collect(Collectors.toMap(Subcommand::getName, subcommand -> subcommand));

    @Override
    public boolean accept(SlashCommandInteractionEvent event) {
        return ParentCommand.super.accept(event) &&
            CommandHelper.acceptIfHasRoles(event, AsyncTI4DiscordBot.developerRoles);
    }

    @Override
    public String getName() {
        return Constants.ADMIN;
    }

    @Override
    public String getDescription() {
        return "Admin";
    }

    @Override
    public Map<String, Subcommand> getSubcommands() {
        return subcommands;
    }
}
