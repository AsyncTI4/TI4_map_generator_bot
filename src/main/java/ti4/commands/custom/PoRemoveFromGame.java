package ti4.commands.custom;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;

class PoRemoveFromGame extends GameStateSubcommand {

    public PoRemoveFromGame() {
        super(Constants.REMOVE_PO_FROM_GAME, "Remove a public objective from the game", true, true);
        addOptions(new OptionData(OptionType.STRING, Constants.PO_ID, "Public objective ID").setRequired(true).setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        boolean removed = getGame().removePOFromGame(event.getOption(Constants.PO_ID).getAsString());
        if (removed) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Public objective removed from game deck.");
        } else {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Public objective not found in game deck.");
        }
    }
}
