package ti4.discord.interactions.slashcommands.admin;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.discord.JdaService;
import ti4.discord.interactions.slashcommands.CommandHelper;
import ti4.discord.interactions.slashcommands.ParentCommand;
import ti4.discord.interactions.slashcommands.Subcommand;
import ti4.helpers.Constants;

public class AdminCommand implements ParentCommand {

    private final Map<String, Subcommand> subcommands = Stream.of(
                    new DeleteGame(),
                    new DisableBot(),
                    new UpdateVanityContainer(),
                    new TourneyWinner(),
                    new DeletePersistenceManagerFile(),
                    new CardsInfoForPlayer(),
                    new UpdateThreadArchiveTime(),
                    new GetChannelHtml())
            .collect(Collectors.toMap(Subcommand::getName, subcommand -> subcommand));

    @Override
    public boolean accept(SlashCommandInteractionEvent event) {
        return ParentCommand.super.accept(event) && CommandHelper.acceptIfHasRoles(event, JdaService.adminRoles);
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
