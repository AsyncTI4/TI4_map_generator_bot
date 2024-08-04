package ti4.commands.custom;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.GameSaveLoadManager;

public class FixSODeck extends CustomSubcommandData {
    public FixSODeck() {
        super(Constants.FIX_SO_DECK, "Put any removed secret objectives back into the deck.");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getActiveGame();
        game.fixScrewedSOs();
        GameSaveLoadManager.saveMap(game, event);
    }
}
