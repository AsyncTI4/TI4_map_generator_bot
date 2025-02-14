package ti4.service.leader;

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
        if (playerLeader == null || !playerLeader.isLocked()) {
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
                    || scoredPublic.getKey().contains("Throne of the False Emperor")) {
                if (scoredPublic.getValue().contains(player.getUserID())) {
                    scoredPOCount++;
                }
            }
        }
        int scoredObjectiveCount = scoredPOCount + scoredSOCount;
        if (scoredObjectiveCount >= 3) {
            UnlockLeaderService.unlockLeader("hero", game, player);
        }
    }
}
