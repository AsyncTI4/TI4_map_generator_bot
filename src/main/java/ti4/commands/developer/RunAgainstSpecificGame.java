package ti4.commands.developer;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.Subcommand;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.persistence.GameManager;
import ti4.message.MessageHelper;
import ti4.message.logging.BotLogger;

class RunAgainstSpecificGame extends Subcommand {

    RunAgainstSpecificGame() {
        super("run_against_specific_game", "Runs this custom code against a specific game.");
        addOptions(new OptionData(OptionType.STRING, Constants.GAME_NAME, "The game to run the command against.")
                .setRequired(true)
                .setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String gameName = event.getOption(Constants.GAME_NAME).getAsString();
        if (!GameManager.isValid(gameName)) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Can't find map for game name " + gameName);
            return;
        }

        MessageHelper.sendMessageToChannel(event.getChannel(), "Running custom command against " + gameName + ".");

        Game game = GameManager.getManagedGame(gameName).getGame();
        boolean changed = makeChanges(game);
        if (changed) {
            BotLogger.info("Changes made to " + game.getName() + ".");
            GameManager.save(game, "Developer ran custom command against this game, probably migration related.");
            MessageHelper.sendMessageToChannel(event.getChannel(), "Finished custom command against " + game.getName() + ".");
        } else {
            MessageHelper.sendMessageToChannel(event.getChannel(), "No changes required for " + game.getName() + ".");
        }
    }

    private static boolean makeChanges(Game game) {
        if (!game.isThundersEdge()) {
            return false;
        }
        game.setThundersEdge(false);
        return true;
    }
}
