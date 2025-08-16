package ti4.service.agenda;

import java.util.Objects;
import lombok.experimental.UtilityClass;
import ti4.map.Game;
import ti4.map.Player;

@UtilityClass
public class IsPlayerElectedService {

    public static boolean isPlayerElected(Game game, Player player, String lawId) {
        if (player == null) {
            return false;
        }
        if ("yes"
                .equalsIgnoreCase(
                        game.getStoredValue("lawsDisabled"))) { // TODO: we should move away from these and use fields.
            return false;
        }
        return game.getLaws().keySet().stream()
                .filter(currentLawId -> currentLawId.equalsIgnoreCase(lawId))
                .map(currentLawId -> game.getLawsInfo().get(currentLawId))
                .filter(Objects::nonNull)
                .anyMatch(lawInfo ->
                        lawInfo.equalsIgnoreCase(player.getFaction()) || lawInfo.equalsIgnoreCase(player.getColor()));
    }
}
