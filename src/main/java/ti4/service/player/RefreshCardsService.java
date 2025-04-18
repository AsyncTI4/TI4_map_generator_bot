package ti4.service.player;

import java.util.ArrayList;
import java.util.List;

import lombok.experimental.UtilityClass;
import ti4.map.Game;
import ti4.map.Leader;
import ti4.map.Player;
import ti4.service.leader.RefreshLeaderService;

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
            List<Leader> leads = new ArrayList<>(player.getLeaders());
            for (Leader leader : leads) {
                if (!leader.isLocked()) {
                    if (leader.isActive() && !leader.getId().equalsIgnoreCase("zealotshero")) {
                        player.removeLeader(leader.getId());
                    } else {
                        RefreshLeaderService.refreshLeader(player, leader, game);
                    }
                }
            }
        }
    }
}
