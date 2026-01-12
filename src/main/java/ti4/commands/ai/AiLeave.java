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
 * Command to remove an AI player from a game.
 * Usage: /ai leave
 */
class AiLeave extends GameStateSubcommand {

    @Autowired
    private AiScheduler aiScheduler;

    public AiLeave() {
        super("leave", "Remove an AI player from this game", true, false);
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Color of the AI player to remove")
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

        // Unregister the AI player
        aiScheduler.unregisterAiPlayer(game.getName(), playerId);
        
        MessageHelper.sendMessageToEventChannel(event, 
            String.format("Removed AI player '%s' from game '%s'.", playerId, game.getName()));
    }
}
