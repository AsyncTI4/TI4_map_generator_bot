package ti4.discord.interactions.commands.admin;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.discord.JdaService;
import ti4.discord.interactions.commands.AbstractParentCommand;
import ti4.discord.interactions.commands.CommandHelper;
import ti4.helpers.Constants;

public class AdminCommand extends AbstractParentCommand {

    public AdminCommand() {
        super(
                Constants.ADMIN,
                "Admin",
                new DeleteGame(),
                new DisableBot(),
                new UpdateVanityContainer(),
                new TourneyWinner(),
                new DeletePersistenceManagerFile(),
                new CardsInfoForPlayer(),
                new UpdateThreadArchiveTime(),
                new GetChannelHtml());
    }

    @Override
    public boolean accept(SlashCommandInteractionEvent event) {
        return super.accept(event) && CommandHelper.acceptIfHasRoles(event, JdaService.adminRoles);
    }
}
