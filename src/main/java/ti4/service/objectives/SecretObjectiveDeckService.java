package ti4.service.objectives;

import java.util.Collection;
import java.util.Collections;
import lombok.experimental.UtilityClass;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
public class SecretObjectiveDeckService {

    public static void shuffleIntoDeck(Game game, Collection<String> soIDs) {
        for (String soID : soIDs) {
            if (!isInGame(game, soID)) {
                game.addSOToGame(soID);
            }
        }
        Collections.shuffle(game.getSecretObjectives());
    }

    public static void removeFromDeck(Game game, Collection<String> soIDs) {
        game.getSecretObjectives().removeAll(soIDs);
    }

    private static boolean isInGame(Game game, String soID) {
        if (game.getSecretObjectives().contains(soID)) return true;
        for (Player player : game.getPlayers().values()) {
            if (player.getSecrets().containsKey(soID)) return true;
            if (player.getSecretsScored().containsKey(soID) && !game.isSecretsOnlyMode()) return true;
            if (SecretsOnlyService.isInPendingOffer(player, soID)) return true;
        }
        return false;
    }
}
