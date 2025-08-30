package ti4.commands.statistics;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands.Subcommand;
import ti4.json.PersistenceManager;
import ti4.message.MessageHelper;
import ti4.message.logging.BotLogger;
import ti4.service.statistics.MatchmakingRatingService;
import ti4.service.statistics.MatchmakingRatingService.MatchmakingRatingData;

class MatchMakingRatingCommand extends Subcommand {

    MatchMakingRatingCommand() {
        super("matchmaking_rating", "Calculates the top 50 high confidence MMRs using the TrueSkill algorithm");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        try {
            MatchmakingRatingData data = PersistenceManager.readObjectFromJsonFile(
                    MatchmakingRatingService.MATCHMAKING_RATING_FILE, MatchmakingRatingData.class);
            if (data == null) {
                MessageHelper.sendMessageToThread(event.getChannel(), "Player Matchmaking Ratings",
                        "Matchmaking ratings have not been calculated yet.");
                return;
            }
            String message = MatchmakingRatingService.buildMessage(data, event.getUser().getId());
            MessageHelper.sendMessageToThread(event.getChannel(), "Player Matchmaking Ratings", message);
        } catch (Exception e) {
            BotLogger.error("Failed to load matchmaking rating data.", e);
            MessageHelper.sendMessageToThread(
                    event.getChannel(), "Player Matchmaking Ratings", "Failed to load matchmaking ratings.");
        }
    }
}
