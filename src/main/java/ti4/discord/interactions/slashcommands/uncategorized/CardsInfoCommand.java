package ti4.discord.interactions.slashcommands.uncategorized;

import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.apache.commons.lang3.function.Consumers;
import ti4.discord.interactions.slashcommands.GameStateCommand;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.Constants;
import ti4.logging.BotLogger;
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
        return "Send to your `#cards-info` thread: secret objectives, action cards, and promissory notes";
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        Player player = getPlayer();
        game.checkPromissoryNotes();
        if (!game.isFowMode()) {
            ThreadChannel channel = player.getCardsInfoThread();
            channel.getManager()
                    .setArchived(true)
                    .queue(
                            Consumers.nop(),
                            BotLogger::catchRestError); // archiving it to combat a common bug that is solved via
            // archiving
        }
        CardsInfoService.sendCardsInfo(game, player, event);
    }
}
