package ti4.commands.custom;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class OfferAFKTimeOptions extends CustomSubcommandData {
    public OfferAFKTimeOptions() {
        super(Constants.OFFER_AFKTIME_OPTIONS, "Offer hours in UTC which you'll be afk usually in (sleeping)");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game activeGame = getActiveGame();
        Player mainPlayer = activeGame.getPlayer(getUser().getId());
        mainPlayer = Helper.getGamePlayer(activeGame, mainPlayer, event, null);
        mainPlayer = Helper.getPlayer(activeGame, mainPlayer, event);
        if (mainPlayer == null) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Player/Faction/Color could not be found in map:" + activeGame.getName());
            return;
        }
        ButtonHelper.offerAFKTimeOptions(activeGame, mainPlayer);
        MessageHelper.sendMessageToChannel(event.getChannel(), "Offered AFK options to " + ButtonHelper.getIdent(mainPlayer));
    }
}
