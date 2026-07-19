package ti4.service.player;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.Ponthous.PonthousAbilityHandler;
import ti4.game.Game;
import ti4.game.Leader;
import ti4.game.Player;
import ti4.helpers.ButtonHelperHeroes;
import ti4.service.leader.RefreshLeaderService;

@UtilityClass
public class RefreshCardsService {
    public static void refreshPlayerCards(Game game, Player player, boolean isStatusPhaseCleanup) {
        if (isStatusPhaseCleanup) {
            PonthousAbilityHandler.resetFracturedSouls(game, player);
        }

        boolean planetsOnly = !isStatusPhaseCleanup;
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
                    if (leader.isActive() && !"zealotshero".equalsIgnoreCase(leader.getId())) {
                        player.removeLeader(leader.getId());
                        ButtonHelperHeroes.checkForMykoHero(game, leader.getId(), player);
                    } else {
                        RefreshLeaderService.refreshLeader(player, leader, game);
                    }
                }
            }
        }
    }
}
