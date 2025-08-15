package ti4.service.leader;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import lombok.experimental.UtilityClass;
import ti4.helpers.Constants;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.Leader;
import ti4.map.Player;

@UtilityClass
public class HeroUnlockCheckService {
    public static void checkIfHeroUnlocked(Game game, Player player) {
        Leader playerLeader = player.getLeader(Constants.HERO).orElse(null);
        if (game.isLiberationC4Mode() && "nekro".equalsIgnoreCase(player.getFaction())) {
            for (Player p2 : game.getRealPlayers()) {
                if (!"nekro".equalsIgnoreCase(p2.getFaction())) {
                    checkIfHeroUnlocked(game, p2);
                }
            }
        }
        if (playerLeader == null) {
            return;
        }
        int scoredSOCount = player.getSecretsScored().size();
        int scoredPOCount = 0;
        Map<String, List<String>> playerScoredPublics = game.getScoredPublicObjectives();
        for (Entry<String, List<String>> scoredPublic : playerScoredPublics.entrySet()) {
            if (Mapper.getPublicObjectivesStage1().containsKey(scoredPublic.getKey())
                    || Mapper.getPublicObjectivesStage2().containsKey(scoredPublic.getKey())
                    || game.getSoToPoList().contains(scoredPublic.getKey())
                    || game.getSoToPoList().stream()
                            .map(Mapper::getSecretObjective)
                            .filter(Objects::nonNull)
                            .anyMatch(so -> so.getName().equals(scoredPublic.getKey()))
                    || scoredPublic.getKey().contains("Throne of the False Emperor")
                    || scoredPublic.getKey().contains("Liberate Ordinian")
                    || scoredPublic.getKey().contains("Control Ordinian")) {
                if (scoredPublic.getValue().contains(player.getUserID())) {
                    scoredPOCount++;
                }
            }
        }
        int scoredObjectiveCount = scoredPOCount + scoredSOCount;
        List<Leader> heroesToUnlock = new ArrayList<>();
        for (Leader leader : player.getLeaders()) {
            if (leader.getId().contains("orlandohero")) {
                Player nekro = game.getPlayerFromColorOrFaction("nekro");
                if (nekro != null && leader.isLocked() && nekro.getTotalVictoryPoints() > 4) {
                    heroesToUnlock.add(leader);
                }
            } else {
                if (leader.getId().contains("hero") && leader.isLocked() && scoredObjectiveCount >= 3) {
                    heroesToUnlock.add(leader);
                }
            }
        }
        for (Leader hero : heroesToUnlock) {
            UnlockLeaderService.unlockLeader(hero.getId(), game, player);
        }
    }
}
