package ti4.commands.custom;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands2.CommandHelper;
import ti4.helpers.Constants;
import ti4.helpers.PlayerPreferenceHelper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class OfferAFKTimeOptions extends CustomSubcommandData {
    public OfferAFKTimeOptions() {
        super(Constants.OFFER_AFKTIME_OPTIONS, "Offer hours in UTC which you'll be afk usually in (sleeping)");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getActiveGame();
        Player player = CommandHelper.getPlayerFromEvent(game, event);
        if (player == null) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Player/Faction/Color could not be found in map:" + game.getName());
            return;
        }
        PlayerPreferenceHelper.offerAFKTimeOptions(game, player);
        MessageHelper.sendMessageToChannel(event.getChannel(), "Offered AFK options to " + player.getFactionEmoji());
    }
}
