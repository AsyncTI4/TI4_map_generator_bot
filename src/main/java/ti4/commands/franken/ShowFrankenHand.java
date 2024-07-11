package ti4.commands.franken;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.helpers.Constants;
import ti4.helpers.FrankenDraftHelper;
import ti4.map.Game;
import ti4.map.Player;

public class ShowFrankenHand extends FrankenSubcommandData {
    public ShowFrankenHand() {
        super(Constants.SHOW_HAND, "Shows your current hand of drafted cards");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getActiveGame();
        Player player = game.getPlayer(getUser().getId());
        FrankenDraftHelper.displayPlayerHand(game, player);
    }
}
