package ti4.discord.interactions.commands.developer;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.apache.commons.lang3.function.Consumers;
import ti4.contest.replay.service.LazaxSeasonService;
import ti4.discord.interactions.commands.Subcommand;
import ti4.logging.BotLogger;
import ti4.spring.context.SpringContext;

class StartLazaxSeason1 extends Subcommand {

    StartLazaxSeason1() {
        super("start_lazax_season_1", "Start Lazax Season 1 with delegation setup");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        boolean posted = SpringContext.getBean(LazaxSeasonService.class).postSeasonOneOpeningMessage();
        String response = posted
                ? "Posted the Lazax Season 1 opening message in `#lazax-war-archives`, assigned delegations, and posted delegation briefings."
                : "Could not find `#lazax-war-archives`.";
        event.getHook().editOriginal(response).queue(Consumers.nop(), BotLogger::catchRestError);
    }
}
