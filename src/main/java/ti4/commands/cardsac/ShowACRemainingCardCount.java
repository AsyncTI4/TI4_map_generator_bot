package ti4.commands.cardsac;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.message.MessageHelper;

public class ShowACRemainingCardCount extends ACCardsSubcommandData {
    public ShowACRemainingCardCount() {
        super(Constants.SHOW_AC_REMAINING_CARD_COUNT, "Show Action Card deck card count");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getActiveGame();

        String sb = "Action cards count in deck is: " + game.getActionCards().size();
        MessageHelper.sendMessageToChannel(event.getChannel(), sb);
    }
}
