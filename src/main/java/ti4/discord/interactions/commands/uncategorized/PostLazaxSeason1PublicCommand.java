package ti4.discord.interactions.commands.uncategorized;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.apache.commons.lang3.function.Consumers;
import ti4.contest.replay.service.LazaxSeasonService;
import ti4.discord.interactions.commands.ParentCommand;
import ti4.logging.BotLogger;
import ti4.spring.context.SpringContext;

public class PostLazaxSeason1PublicCommand implements ParentCommand {

    @Override
    public String getName() {
        return "post_lazax_season_1_public";
    }

    @Override
    public String getDescription() {
        return "Post only the Lazax Season 1 public opening message";
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        boolean posted = SpringContext.getBean(LazaxSeasonService.class).postSeasonOnePublicOpeningMessage();
        String response = posted
                ? "Posted the Lazax Season 1 public opening message in `#lazax-war-archives` without assigning delegations or posting delegation briefings."
                : "Could not find `#lazax-war-archives`.";
        event.getHook().editOriginal(response).queue(Consumers.nop(), BotLogger::catchRestError);
    }
}
