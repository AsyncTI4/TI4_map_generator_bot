package ti4.commands2.uncategorized;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands2.GameStateCommand;
import ti4.helpers.Constants;
import ti4.helpers.PromissoryNoteHelper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.service.info.CardsInfoService;

public class CardsInfoCommand extends GameStateCommand {

    public CardsInfoCommand() {
        super(false, true);
    }

    @Override
    public String getName() {
        return Constants.CARDS_INFO;
    }

    @Override
    public String getDescription() {
        return "Send to your `#cards-info` thread: scored & unscored secret objectives, action cards, and promissory notes both in hand and in your play area";
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        Player player = getPlayer();
        game.checkPromissoryNotes();
        PromissoryNoteHelper.checkAndAddPNs(game, player);
        CardsInfoService.sendCardsInfo(game, player, event);
    }
}
