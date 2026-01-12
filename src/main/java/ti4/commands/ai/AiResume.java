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
 * Command to resume a paused AI player.
 * Usage: /ai resume
 */
class AiResume extends GameStateSubcommand {

    @Autowired
    private AiScheduler aiScheduler;

    public AiResume() {
        super("resume", "Resume a paused AI player", true, false);
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Color of the AI player")
                .setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        String playerId = event.getOption(Constants.FACTION_COLOR).getAsString();

        // Check if it's an AI player
        if (!aiScheduler.isAiPlayer(game.getName(), playerId)) {
            MessageHelper.sendMessageToEventChannel(event, 
                "Player '" + playerId + "' is not an AI player.");
            return;
        }

        // Resume the AI player
        aiScheduler.resumeAiPlayer(game.getName(), playerId);
        
        MessageHelper.sendMessageToEventChannel(event, 
            String.format("Resumed AI player '%s'.", playerId));
    }
}
