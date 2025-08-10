package ti4.commands.explore;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.message.MessageHelper;

class ExploreShuffle extends GameStateSubcommand {

    public ExploreShuffle() {
        super(Constants.SHUFFLE_EXPLORES, "Shuffle all explores and relics", true, true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {

        Game game = getGame();
        game.shuffleExplores();
        game.shuffleRelics();
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Shuffled all explores and relics");
    }
}
