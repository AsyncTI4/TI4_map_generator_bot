package ti4.service.relic;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.helpers.Helper;
import ti4.helpers.RelicHelper;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.RelicModel;

@UtilityClass
public class SendRelicService {

    public static void handleSendRelic(
            GenericInteractionCreateEvent event, Game game, Player player1, Player player2, String relicID) {
        boolean exhausted = player1.getExhaustedRelics().contains(relicID);
        if ("thetriad".equals(relicID)) {
            exhausted = player1.getExhaustedPlanets().contains("triad");
        }

        // Transfer the relic
        player1.removeRelic(relicID);
        player2.addRelic(relicID);

        // Remove points etc from p1, then resolve effects for p2
        RelicHelper.resolveRelicLossEffects(game, player1, relicID);
        RelicHelper.resolveRelicEffects(event, game, player2, relicID);

        // Additionally exhaust the relic after gaining, if applicable
        if (exhausted && !"thetriad".equals(relicID)) player2.addExhaustedRelic(relicID);
        if ("thetriad".equals(relicID)) {
            if (exhausted) player2.exhaustPlanet("triad");
        }

        if (player1.hasRelic(relicID) || !player2.hasRelic(relicID)) {
            MessageHelper.sendMessageToEventChannel(
                    event,
                    "Something may have gone wrong - please check your relics and ping Bothelper if there is a problem.");
            return;
        }

        RelicModel relicModel = Mapper.getRelic(relicID);
        String sb = player1.getRepresentation() + " sent a relic to "
                + player2.getRepresentation() + "\n"
                + relicModel.getSimpleRepresentation();
        MessageHelper.sendMessageToChannel(player1.getCorrectChannel(), sb);
        if (game.isFowMode()) MessageHelper.sendMessageToChannel(player2.getCorrectChannel(), sb);
        Helper.checkEndGame(game, player2);
    }
}
