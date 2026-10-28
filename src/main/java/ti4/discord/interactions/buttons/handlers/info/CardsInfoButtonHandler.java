package ti4.discord.interactions.buttons.handlers.info;

import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import org.apache.commons.lang3.function.Consumers;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;
import ti4.service.breakthrough.EidolonMaximumService;
import ti4.service.info.CardsInfoService;
import ti4.service.info.KnownCardsService;

@UtilityClass
class CardsInfoButtonHandler {

    @ButtonHandler(value = "cardsInfo", save = false)
    public static void sendCardsInfo(Game game, Player player, GenericInteractionCreateEvent event) {
        if (player == null) {
            return;
        }
        if (!game.isFowMode()) {
            ThreadChannel channel = player.getCardsInfoThread();
            // archiving it to combat a common bug that is solved via archiving
            channel.getManager().setArchived(true).queue(Consumers.nop(), BotLogger::catchRestError);
        }
        CardsInfoService.sendCardsInfo(game, player, event);
        EidolonMaximumService.sendEidolonMaximumFlipButtons(game, player);
    }

    @ButtonHandler(value = "showKnownCardHands", save = false)
    public static void showKnownCardHands(Game game, Player player) {
        if (player == null || !KnownCardsService.isEnabled(game)) {
            return;
        }
        List<MessageEmbed> embeds = KnownCardsService.getKnownCardHandEmbeds(game, player);
        if (embeds.isEmpty()) {
            MessageHelper.sendMessageToPlayerCardsInfoThread(player, "No known cards.");
            return;
        }
        MessageHelper.sendMessageEmbedsToCardsInfoThread(player, "__Known Card Hands__", embeds);
    }
}
