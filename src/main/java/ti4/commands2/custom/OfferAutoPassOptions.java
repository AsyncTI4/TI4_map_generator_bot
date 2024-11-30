package ti4.commands2.custom;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands2.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.helpers.PlayerPreferenceHelper;
import ti4.message.MessageHelper;

class OfferAutoPassOptions extends GameStateSubcommand {

    public OfferAutoPassOptions() {
        super(Constants.OFFER_AUTOPASS_OPTIONS, "Offer auto pass on Sabotages to every player in the game.", true, false);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        PlayerPreferenceHelper.offerSetAutoPassOnSaboButtons(getGame(), null);
        MessageHelper.sendMessageToChannel(event.getChannel(), "Offered options");
    }
}
