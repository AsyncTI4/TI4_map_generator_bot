package ti4.discord.interactions.commands.lazax;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.contest.replay.service.LazaxSeasonService;
import ti4.discord.interactions.commands.Subcommand;
import ti4.spring.context.SpringContext;

class LazaxStartSeason1 extends Subcommand {

    private static final String AUTHORIZED_USER_ID = "139760548471504897";

    LazaxStartSeason1() {
        super("start_season_1", "Start Lazax Season 1 with delegation setup.");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (!AUTHORIZED_USER_ID.equals(event.getUser().getId())) {
            LazaxReplyHelper.replyEphemeral(event, "You are not authorized to start Lazax Season 1.");
            return;
        }

        boolean posted = SpringContext.getBean(LazaxSeasonService.class).postSeasonOneOpeningMessage();
        String response = posted
                ? "Posted the Lazax Season 1 opening message in `#lazax-war-archives`, assigned delegations, and posted delegation briefings."
                : "Could not find `#lazax-war-archives`.";
        LazaxReplyHelper.replyEphemeral(event, response);
    }

    @Override
    public boolean isEphemeral(SlashCommandInteractionEvent event) {
        return true;
    }
}
