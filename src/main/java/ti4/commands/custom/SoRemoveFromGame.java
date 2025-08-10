package ti4.commands.custom;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.message.MessageHelper;

class SoRemoveFromGame extends GameStateSubcommand {

    public SoRemoveFromGame() {
        super(Constants.REMOVE_SO_FROM_GAME, "Removes a secret objective from the game", true, true);
        addOptions(new OptionData(OptionType.STRING, Constants.SO_ID, "Secret objective ID")
                .setRequired(true)
                .setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        boolean removed = game.removeSOFromGame(event.getOption(Constants.SO_ID).getAsString());
        if (removed) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Secret objective removed from game deck.");
        } else {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Secret objective not found in game deck.");
        }
    }
}
