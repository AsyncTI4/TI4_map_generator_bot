package ti4.ai.letnev;

import lombok.experimental.UtilityClass;
import ti4.game.Player;

@UtilityClass
public class L4Disruptors {

    private static final String TECH = "l4";
    private static final String BROKERED_TECH = "l4_br";

    public static boolean shieldsInvaders(Player invader) {
        return invader.hasTech(TECH) || invader.hasTech(BROKERED_TECH);
    }
}
