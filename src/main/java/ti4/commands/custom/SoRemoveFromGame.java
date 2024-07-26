package ti4.commands.custom;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.message.MessageHelper;

public class SoRemoveFromGame extends CustomSubcommandData {
    public SoRemoveFromGame() {
        super(Constants.REMOVE_SO_FROM_GAME, "Remove a secret objective from the game.");
        addOptions(new OptionData(OptionType.STRING, Constants.SO_ID, "Secret objective ID").setRequired(true).setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getActiveGame();

        OptionMapping soOption = event.getOption(Constants.SO_ID);
        if (soOption == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Specify secret objective.");
            return;
        }
        boolean removed = game.removeSOFromGame(soOption.getAsString());
        if (removed) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Secret objective was removed from game deck.");
        } else {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Secret objective was not found in game deck.");
        }
    }
}
