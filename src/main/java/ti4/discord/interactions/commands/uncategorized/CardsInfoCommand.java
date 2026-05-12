package ti4.discord.interactions.commands.uncategorized;

import java.util.List;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.apache.commons.lang3.function.Consumers;
import ti4.discord.interactions.commands.GameStateCommand;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.Constants;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;
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
    public List<OptionData> getOptions() {
        return List.of(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or color", false, true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Player player = getPlayer();
        if (!player.isRealPlayer()) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "This command only works on real players.");
            return;
        }

        Game game = getGame();
        if (!game.isFowMode()) {
            ThreadChannel channel = player.getCardsInfoThread();
            if (channel == null) {
                MessageHelper.sendEphemeralMessageToEventChannel(
                        event, "Unable to find the player's cards info thread.");
                return;
            }
            channel.getManager()
                    .setArchived(true)
                    // archiving it to combat a common bug that is solved via archiving
                    .queue(Consumers.nop(), BotLogger::catchRestError);
        }
        CardsInfoService.sendCardsInfo(game, player, event);
    }
}
