package ti4.commands.franken;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.helpers.Constants;
import ti4.helpers.FrankenDraftHelper;
import ti4.map.Game;
import ti4.map.Player;

public class ShowFrankenBag extends FrankenSubcommandData {
    public ShowFrankenBag() {
        super(Constants.SHOW_BAG, "Shows your current FrankenDraft bag of cards left to draft.");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getActiveGame();
        Player player = game.getPlayer(getUser().getId());
        FrankenDraftHelper.showPlayerBag(game, player);
    }
}
