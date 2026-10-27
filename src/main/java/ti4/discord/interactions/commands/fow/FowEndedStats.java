package ti4.discord.interactions.commands.fow;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.discord.JdaService;
import ti4.discord.interactions.commands.CommandHelper;
import ti4.discord.interactions.commands.Subcommand;
import ti4.discord.interactions.commands.statistics.GameStatisticsFilterer;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;
import ti4.service.persistence.DatabasePersistenceGate;
import ti4.service.statistics.FogEndedGameStatisticsService;

class FowEndedStats extends Subcommand {

    public FowEndedStats() {
        super(Constants.FOW_ENDED_STATS, "Settings, factions and options used by ended fog games (admin/developer)");
        addOptions(GameStatisticsFilterer.gameStatsFiltersExcept(GameStatisticsFilterer.FOG_FILTER));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (!CommandHelper.hasRole(event, JdaService.developerRoles)) {
            MessageHelper.replyToMessage(event, "Only admins and developers can use this command.");
            return;
        }
        if (DatabasePersistenceGate.isDisabled()) {
            MessageHelper.replyToMessage(
                    event, "Statistics are temporarily unavailable while database maintenance is in progress.");
            return;
        }
        FogEndedGameStatisticsService.queueReply(event);
    }
}
