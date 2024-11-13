package ti4.commands.cardsac;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.message.MessageHelper;

public class ShowACRemainingCardCount extends GameStateSubcommand {
    public ShowACRemainingCardCount() {
        super(Constants.SHOW_AC_REMAINING_CARD_COUNT, "Show Action Card deck card count", false, false, false);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        String sb = "Action cards count in deck is: " + game.getActionCards().size();
        MessageHelper.sendMessageToChannel(event.getChannel(), sb);
    }
}
