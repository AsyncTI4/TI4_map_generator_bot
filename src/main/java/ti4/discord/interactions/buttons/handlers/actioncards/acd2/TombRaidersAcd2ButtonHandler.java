package ti4.discord.interactions.buttons.handlers.actioncards.acd2;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.function.Consumers;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.Constants;
import ti4.image.Mapper;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;
import ti4.model.ExploreModel;
import ti4.service.leader.CommanderUnlockCheckService;

@UtilityClass
class TombRaidersAcd2ButtonHandler {

    @ButtonHandler("resolveTombRaiders")
    public static void resolveTombRaiders(Player player, Game game, ButtonInteractionEvent event) {
        player.gainCommodities(2);
        List<String> types = new ArrayList<>(List.of("hazardous", "cultural", "industrial", "frontier"));
        StringBuilder sb = new StringBuilder();
        sb.append(player.getRepresentationUnfogged()).append(" gained 2 commodities and:");
        for (String type : types) {
            String cardId = game.drawExplore(type);
            ExploreModel card = Mapper.getExplore(cardId);
            String cardType = card.getResolution();
            sb.append("\nRevealed _")
                    .append(card.getName())
                    .append("_ from the top of the ")
                    .append(type)
                    .append(" deck and ");
            if (Constants.FRAGMENT.equalsIgnoreCase(cardType)) {
                sb.append("gained it.");
                player.addFragment(cardId);
                game.purgeExplore(cardId);
            } else {
                sb.append("discarded it.");
            }
        }
        CommanderUnlockCheckService.checkPlayer(player, "kollecc");
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), sb.toString());
        event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
    }
}
