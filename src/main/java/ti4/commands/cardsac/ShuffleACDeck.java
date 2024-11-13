package ti4.commands.cardsac;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.message.MessageHelper;

public class ShuffleACDeck extends GameStateSubcommand {

    public ShuffleACDeck() {
        super(Constants.SHUFFLE_AC_DECK, "Shuffle Action Card deck", true, false);
        addOptions(new OptionData(OptionType.STRING, Constants.CONFIRM, "Confirm undo command with YES").setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();

        String confirm = event.getOption(Constants.CONFIRM).getAsString();
        if (!"YES".equals(confirm)) {
            MessageHelper.replyToMessage(event, "Must confirm with YES");
            return;
        }

        game.shuffleActionCards();
        MessageHelper.replyToMessage(event, "Action card deck was shuffled");
    }
}
