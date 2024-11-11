package ti4.commands.custom;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands.PlayerGameStateSubcommand;
import ti4.helpers.Constants;
import ti4.helpers.PlayerPreferenceHelper;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class OfferAFKTimeOptions extends PlayerGameStateSubcommand {

    public OfferAFKTimeOptions() {
        super(Constants.OFFER_AFKTIME_OPTIONS, "Offer hours in UTC which you'll be afk usually in (sleeping)", true, true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Player player = getPlayer();
        PlayerPreferenceHelper.offerAFKTimeOptions(player);
        MessageHelper.sendMessageToChannel(event.getChannel(), "Offered AFK options to " + player.getFactionEmoji());
    }
}
