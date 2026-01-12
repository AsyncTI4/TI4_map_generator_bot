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
 * Command to change AI player difficulty.
 * Usage: /ai difficulty <simple|medium|hard>
 */
class AiDifficulty extends GameStateSubcommand {

    @Autowired
    private AiScheduler aiScheduler;

    public AiDifficulty() {
        super("difficulty", "Change AI player difficulty", true, false);
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Color of the AI player")
                .setRequired(true));
        addOptions(new OptionData(OptionType.STRING, "difficulty", "New difficulty level")
                .addChoice("Simple", "simple")
                .addChoice("Medium", "medium")
                .addChoice("Hard", "hard")
                .setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        String playerId = event.getOption(Constants.FACTION_COLOR).getAsString();
        String difficulty = event.getOption("difficulty").getAsString();

        // Check if it's an AI player
        if (!aiScheduler.isAiPlayer(game.getName(), playerId)) {
            MessageHelper.sendMessageToEventChannel(event, 
                "Player '" + playerId + "' is not an AI player.");
            return;
        }

        // Change difficulty
        aiScheduler.changeDifficulty(game.getName(), playerId, difficulty);
        
        MessageHelper.sendMessageToEventChannel(event, 
            String.format("Changed AI player '%s' difficulty to '%s'.", playerId, difficulty));
    }
}
