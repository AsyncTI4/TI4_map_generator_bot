package ti4.service.planet;

import java.util.List;
import lombok.experimental.UtilityClass;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

@UtilityClass
public class EronousPlanetService {
    public static final String CANTRIS_ID = "cantris";
    public static final String CANTRIS_PO = "The Starlight Throne";

    public static void resolveCantrisPO(Game game, String planet, Player player) {
        if (!CANTRIS_ID.equalsIgnoreCase(planet)) return;

        Player previousOwner = getOwner(game, CANTRIS_ID);
        Integer cantrisPOId = game.getRevealedPublicObjectives().get(CANTRIS_PO);
        if (cantrisPOId != null) {
            game.unscorePublicObjective(previousOwner.getUserID(), cantrisPOId);
            if (previousOwner.isRealPlayer()) {
                MessageHelper.sendMessageToChannel(
                        previousOwner.getCorrectChannel(),
                        previousOwner.getRepresentationUnfogged() + " lost Cantris and " + CANTRIS_PO
                                + " victory point.");
            }
        } else if (player.isRealPlayer()) {
            cantrisPOId = game.addCustomPO(CANTRIS_PO, 1);
        }

        if (!player.isRealPlayer()) {
            return;
        }

        game.scorePublicObjective(player.getUserID(), cantrisPOId);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + " captured Cantris and gained " + CANTRIS_PO + " victory point.");
        Helper.checkEndGame(game, player);
    }

    public static List<Player> resolveCantrisScoringOrder(Game game) {
        List<Player> scoringOrder = game.getActionPhaseTurnOrder();
        Player cantrisOwner = getOwner(game, CANTRIS_ID);
        if (cantrisOwner != null && cantrisOwner.isRealPlayer()) {
            scoringOrder.remove(cantrisOwner);
            scoringOrder.addLast(cantrisOwner);
        }
        return scoringOrder;
    }

    private static Player getOwner(Game game, String planet) {
        return game.getPlayers().values().stream()
                .filter(p -> p.getPlanets().contains(planet))
                .findFirst()
                .orElse(null);
    }
}
