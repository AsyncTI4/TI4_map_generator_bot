package ti4.discord.interactions.commands.developer;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.discord.interactions.commands.Subcommand;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;
import ti4.spring.service.statistics.overrule.OverruleStatsService;

class RunAgainstAllGames extends Subcommand {

    RunAgainstAllGames() {
        super("run_against_all_games", "Runs this custom code against all games.");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        MessageHelper.sendMessageToChannel(event.getChannel(), "Migrating overrule_choice rows into overrule_play.");
        int migrated;
        try {
            migrated = OverruleStatsService.get().migrateChoicesToPlays();
        } catch (Exception e) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Migration failed: " + e.getMessage());
            BotLogger.error("Failed to migrate overrule_choice into overrule_play.", e);
            return;
        }
        String summary = "Migrated " + migrated + " Overrule plays from overrule_choice to overrule_play.";
        MessageHelper.sendMessageToChannel(event.getChannel(), summary);
        BotLogger.info(summary);
    }
}
