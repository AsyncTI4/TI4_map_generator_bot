package ti4.commands.ai;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.springframework.beans.factory.annotation.Autowired;
import ti4.ai.AiConfig;
import ti4.ai.AiScheduler;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.message.MessageHelper;

/**
 * Command to add an AI player to a game.
 * Usage: /ai join [difficulty]
 */
class AiJoin extends GameStateSubcommand {

    @Autowired
    private AiScheduler aiScheduler;

    @Autowired
    private AiConfig aiConfig;

    public AiJoin() {
        super("join", "Add an AI player to this game", true, false);
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Color for the AI player")
                .setRequired(true));
        addOptions(new OptionData(OptionType.STRING, "difficulty", "AI difficulty level")
                .addChoice("Simple", "simple")
                .addChoice("Medium", "medium")
                .addChoice("Hard", "hard")
                .setRequired(false));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (!aiConfig.isEnabled()) {
            MessageHelper.sendMessageToEventChannel(event, 
                "AI players are disabled. Enable with `ai.enabled=true` in configuration.");
            return;
        }

        Game game = getGame();
        String playerId = event.getOption(Constants.FACTION_COLOR).getAsString();
        String difficulty = event.getOption("difficulty") != null 
            ? event.getOption("difficulty").getAsString() 
            : aiConfig.getDefaultDifficulty();

        // Validate player exists in game
        var player = game.getPlayer(playerId);
        if (player == null) {
            MessageHelper.sendMessageToEventChannel(event, 
                "Player with color '" + playerId + "' not found in game.");
            return;
        }

        // Check if already an AI player
        if (aiScheduler.isAiPlayer(game.getName(), playerId)) {
            MessageHelper.sendMessageToEventChannel(event, 
                "Player '" + playerId + "' is already an AI player.");
            return;
        }

        // Register the AI player
        aiScheduler.registerAiPlayer(game.getName(), playerId, difficulty);
        
        MessageHelper.sendMessageToEventChannel(event, 
            String.format("Added AI player '%s' with difficulty '%s' to game '%s'.", 
                playerId, difficulty, game.getName()));
    }
}
