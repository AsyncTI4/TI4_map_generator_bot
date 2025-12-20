package ti4.buttons.handlers.info;

import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.image.Mapper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.service.breakthrough.EidolonMaximumService;
import ti4.service.info.CardsInfoService;

@UtilityClass
class CardsInfoButtonHandler {

    @ButtonHandler(value = "cardsInfo", save = false)
    public static void sendCardsInfo(Game game, Player player, GenericInteractionCreateEvent event) {
        if (player == null) {
            return;
        }
        if (!game.isFowMode()) {
            ThreadChannel channel = player.getCardsInfoThread();
            channel.getManager()
                    .setArchived(true)
                    .queue(
                            Consumers.nop(),
                            BotLogger::catchRestError); // archiving it to combat a common bug that is solved via
            // archiving
        }
        List<String> techs = Mapper.getDeck("techs_tf").getNewShuffledDeck();
        for (String tech : techs) {
            if (Mapper.getTech(tech) == null) {
                System.out.println(tech);
            }
        }
        CardsInfoService.sendCardsInfo(game, player, event);
        EidolonMaximumService.sendEidolonMaximumFlipButtons(game, player);
    }
}
