package ti4.commands.cardsac;

import java.util.List;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.message.MessageHelper;

public class ShowAllUnplayedACs extends GameStateSubcommand {

    public ShowAllUnplayedACs() {
        super(Constants.SHOW_UNPLAYED_AC, "Show all unplayed Action Cards", false, false, false);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        showUnplayedACs(getGame(), event);
    }

    @ButtonHandler("showDeck_unplayedAC")
    public static void showUnplayedACs(Game game, GenericInteractionCreateEvent event) {
        List<String> unplayedACs = Helper.unplayedACs(game);
        String title = game.getName() + " -- Unplayed Action Cards";
        String actionCardString = ShowDiscardActionCards.actionCardListCondensedNoIds(unplayedACs, title);
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), actionCardString);
    }
}
