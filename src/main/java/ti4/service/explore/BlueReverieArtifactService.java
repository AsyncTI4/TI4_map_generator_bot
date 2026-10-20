package ti4.service.explore;

import java.util.Set;
import lombok.experimental.UtilityClass;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
public class BlueReverieArtifactService {
    private static final Set<String> ARTIFACT_IDS =
            Set.of("culturalartifact", "hazardousartifact", "industrialartifact", "unknownartifact");

    public static boolean claimArtifact(Game game, Player player, String exploreId) {
        String relicId = getArtifactId(exploreId);
        if (!isArtifact(relicId)) {
            return false;
        }
        game.purgeExplore(exploreId);
        player.addRelic(relicId);
        return true;
    }

    public static boolean discardArtifact(Game game, Player player, String relicId) {
        if (!isArtifact(relicId) || !player.hasRelic(relicId)) {
            return false;
        }
        player.removeRelic(relicId);
        player.removeExhaustedRelic(relicId);
        game.discardExplore(relicId);
        return true;
    }

    public static boolean isArtifact(String relicId) {
        return ARTIFACT_IDS.contains(getArtifactId(relicId));
    }

    private static String getArtifactId(String id) {
        return id == null ? "" : id.replace("extra1", "").replace("extra2", "");
    }
}
