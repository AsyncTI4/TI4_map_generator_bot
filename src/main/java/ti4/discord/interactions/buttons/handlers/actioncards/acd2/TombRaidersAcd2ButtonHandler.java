package ti4.discord.interactions.buttons.handlers.actioncards.acd2;

import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperStats;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;
import ti4.model.ExploreModel;
import ti4.model.Mapper;

@UtilityClass
class TombRaidersAcd2ButtonHandler {

    private static final List<String> EXPLORATION_DECKS =
            List.of(Constants.CULTURAL, Constants.INDUSTRIAL, Constants.HAZARDOUS, Constants.FRONTIER);

    @ButtonHandler("resolveTombRaiders")
    public static void resolveTombRaiders(Player player, Game game, ButtonInteractionEvent event) {
        ButtonHelper.deleteMessage(event);
        StringBuilder sb = new StringBuilder(player.getRepresentationNoPing())
                .append(" revealed the top card of each exploration deck for _Tomb Raiders_:\n");
        int fragments = 0;
        int discarded = 0;
        for (String deck : EXPLORATION_DECKS) {
            String cardID = game.drawExplore(deck);
            if (cardID == null) continue;
            ExploreModel card = Mapper.getExplore(cardID);
            if (card == null) continue;
            sb.append(card.textRepresentation()).append('\n');
            if (Constants.FRAGMENT.equalsIgnoreCase(card.getResolution())) {
                player.addFragment(cardID);
                game.purgeExplore(cardID);
                fragments++;
            } else {
                discarded++;
            }
        }
        if (fragments + discarded == 0) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(), "There were no exploration cards to reveal for _Tomb Raiders_.");
            return;
        }
        sb.append(player.getRepresentationNoPing())
                .append(" gained ")
                .append(fragments)
                .append(fragments == 1 ? " relic fragment" : " relic fragments")
                .append(" and discarded ")
                .append(discarded)
                .append(discarded == 1 ? " card." : " cards.");
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), sb.toString());
        if (discarded > 0) {
            ButtonHelperStats.gainComms(event, game, player, discarded, false);
        }
    }
}
