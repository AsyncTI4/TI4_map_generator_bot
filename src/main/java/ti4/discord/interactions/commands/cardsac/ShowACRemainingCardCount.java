package ti4.discord.interactions.commands.cardsac;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.game.Game;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;

class ShowACRemainingCardCount extends GameStateSubcommand {

    public ShowACRemainingCardCount() {
        super(Constants.SHOW_AC_REMAINING_CARD_COUNT, "Show Action Card deck card count", false, false);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        String sb = "Action cards count in deck is: " + game.getActionCards().size();
        MessageHelper.sendMessageToChannel(event.getChannel(), sb);
    }
}
