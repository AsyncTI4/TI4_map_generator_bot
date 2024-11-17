package ti4.commands.custom;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands2.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;

class PoRemoveFromGame extends GameStateSubcommand {

    public PoRemoveFromGame() {
        super(Constants.REMOVE_PO_FROM_GAME, "PO remove from game", true, true);
        addOptions(new OptionData(OptionType.STRING, Constants.PO_ID, "Public ID").setRequired(true).setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        boolean removed = getGame().removePOFromGame(event.getOption(Constants.PO_ID).getAsString());
        if (removed) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "PO removed from game deck");
        } else {
            MessageHelper.sendMessageToChannel(event.getChannel(), "PO not found in game deck");
        }
    }
}
