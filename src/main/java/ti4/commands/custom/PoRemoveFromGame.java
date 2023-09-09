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
        super(Constants.REMOVE_PO_FROM_GAME, "PO remove from game");
        addOptions(new OptionData(OptionType.STRING, Constants.PO_ID, "Public ID").setRequired(true).setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game activeGame = getActiveGame();

        OptionMapping soOption = event.getOption(Constants.PO_ID);
        if (soOption == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Specify PO");
            return;
        }
        boolean removed = activeGame.removePOFromGame(soOption.getAsString());
        if (removed) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "PO removed from game deck");
        } else {
            MessageHelper.sendMessageToChannel(event.getChannel(), "PO not found in game deck");
        }
    }
}
