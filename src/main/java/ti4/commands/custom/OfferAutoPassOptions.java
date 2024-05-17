package ti4.commands.custom;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.message.MessageHelper;

public class OfferAutoPassOptions extends CustomSubcommandData {
    public OfferAutoPassOptions() {
        super(Constants.OFFER_AUTOPASS_OPTIONS, "Offer auto pass on sabos to every player in the game");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getActiveGame();
        ButtonHelper.offerSetAutoPassOnSaboButtons(game, null);
        MessageHelper.sendMessageToChannel(event.getChannel(), "Offered options");
    }
}
