package ti4.commands.cardsac;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands.cards.CardsInfo;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class ACInfo_Legacy extends ACCardsSubcommandData {

    public static final String CARDS_INFO = Constants.CARDS_INFO_THREAD_PREFIX;

    /**
     * This only still exists because some folks don't want to stop using [/ac info] instead of [/cards_info]
     */
    public ACInfo_Legacy() {
        super(Constants.INFO, "Send all your cards to your Cards Info thread");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game activeGame = getActiveGame();
        Player player = activeGame.getPlayer(getUser().getId());
        player = Helper.getGamePlayer(activeGame, player, event, null);
        if (player == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Player could not be found");
            return;
        }
        String headerText = Helper.getPlayerRepresentation(player, activeGame, activeGame.getGuild(), true) + " used `/cards_info`";
        MessageHelper.sendMessageToPlayerCardsInfoThread(player, activeGame, headerText);
        CardsInfo.sendCardsInfo(activeGame, player);
    }
}
