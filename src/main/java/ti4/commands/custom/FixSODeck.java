package ti4.commands.custom;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.GameSaveLoadManager;

public class FixSODeck extends GameStateSubcommand {

    public FixSODeck() {
        super(Constants.FIX_SO_DECK, "Put back into the deck any removed SOs", true, true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        game.fixScrewedSOs();
        GameSaveLoadManager.saveGame(game, event);
    }
}
