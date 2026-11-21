package ti4.ai.eval;

import java.util.Map;
import lombok.experimental.UtilityClass;
import ti4.game.Player;

@UtilityClass
public class FactionStrength {

    private static final Map<String, Double> BONUS = Map.of();

    public static double bonus(Player player) {
        return player.getFaction() == null ? 0 : BONUS.getOrDefault(player.getFaction(), 0.0);
    }
}
