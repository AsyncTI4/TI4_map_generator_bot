package ti4.ai.winnu;

import lombok.experimental.UtilityClass;
import ti4.game.Player;

@UtilityClass
public class BloodTies {

    public static final String ABILITY = "blood_ties";

    public static boolean waivesCustodiansInfluence(Player player) {
        return player != null && player.hasAbility(ABILITY);
    }
}
