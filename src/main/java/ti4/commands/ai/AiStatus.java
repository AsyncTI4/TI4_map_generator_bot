package ti4.commands.ai;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.springframework.beans.factory.annotation.Autowired;
import ti4.ai.AiScheduler;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.message.MessageHelper;

/**
 * Command to get AI player status.
 * Usage: /ai status
 */
class AiStatus extends GameStateSubcommand {

    @Autowired
    private AiScheduler aiScheduler;

    public AiStatus() {
        super("status", "Get AI player status and statistics", false, false);
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Color of the AI player")
                .setRequired(false));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        String playerId = event.getOption(Constants.FACTION_COLOR) != null 
            ? event.getOption(Constants.FACTION_COLOR).getAsString() 
            : null;

        if (playerId != null) {
            // Get status for specific player
            if (!aiScheduler.isAiPlayer(game.getName(), playerId)) {
                MessageHelper.sendMessageToEventChannel(event, 
                    "Player '" + playerId + "' is not an AI player.");
                return;
            }

            String status = aiScheduler.getStatus(game.getName(), playerId);
            MessageHelper.sendMessageToEventChannel(event, status);
        } else {
            // Get overall AI status
            int aiCount = aiScheduler.getAiPlayerCount();
            MessageHelper.sendMessageToEventChannel(event, 
                String.format("Total AI players across all games: %d", aiCount));
        }
    }
}
