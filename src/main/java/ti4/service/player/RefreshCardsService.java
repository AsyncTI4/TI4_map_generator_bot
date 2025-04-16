package ti4.service.player;

import lombok.experimental.UtilityClass;
import ti4.map.Game;
import ti4.map.Player;

@UtilityClass
public class RefreshCardsService {
    public static void refreshPlayerCards(Game game, Player player, boolean endOfStatusPhase) {
        boolean planetsOnly = endOfStatusPhase;
        if (game.isOmegaPhaseMode()) {
            planetsOnly = !planetsOnly;
        }
        if (planetsOnly) {
            player.clearExhaustedPlanets(false);
        } else {
            player.clearExhaustedTechs();
            player.clearExhaustedPlanets(true);
            player.clearExhaustedRelics();
            player.clearExhaustedAbilities();
        }
    }
}
