package ti4.commands.custom;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.message.MessageHelper;

public class PoRemoveFromGame extends CustomSubcommandData {

    public PoRemoveFromGame() {
        super(Constants.REMOVE_PO_FROM_GAME, "Remove a public objective from the game.");
        addOptions(new OptionData(OptionType.STRING, Constants.PO_ID, "Public objective ID").setRequired(true).setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getActiveGame();

        OptionMapping soOption = event.getOption(Constants.PO_ID);
        if (soOption == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Specify a public objective ID.");
            return;
        }
        boolean removed = game.removePOFromGame(soOption.getAsString());
        if (removed) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Public objective removed from game deck.");
        } else {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Public objective not found in game deck.");
        }
    }
}
