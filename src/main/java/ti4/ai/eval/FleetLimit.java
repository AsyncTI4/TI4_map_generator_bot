package ti4.ai.eval;

import java.util.function.Supplier;
import javax.annotation.Nullable;
import lombok.experimental.UtilityClass;
import ti4.game.Leader;
import ti4.game.Player;

@UtilityClass
public class FleetLimit {

    public static final int UNLIMITED = 1000;
    private static final String ARMADA = "armada";
    private static final int ARMADA_ROOM = 2;
    private static final String LETNEV_HERO = "letnevhero";
    private static final ThreadLocal<Player> UNLIMITED_ASSUMED_FOR = new ThreadLocal<>();

    public static int bonus(Player seat) {
        int mahact = Math.max(0, seat.getEffectiveFleetCC() - seat.getFleetCC());
        return mahact + (seat.hasAbility(ARMADA) ? ARMADA_ROOM : 0);
    }

    public static int ships(Player seat) {
        if (UNLIMITED_ASSUMED_FOR.get() == seat || heroLiftsLimit(seat)) return UNLIMITED;
        return seat.getFleetCC() + bonus(seat);
    }

    public static boolean heroLiftsLimit(Player seat) {
        return seat.getLeader(LETNEV_HERO).map(Leader::isActive).orElse(false);
    }

    public static <T> T assumingUnlimited(Player seat, Supplier<T> evaluation) {
        @Nullable Player before = UNLIMITED_ASSUMED_FOR.get();
        UNLIMITED_ASSUMED_FOR.set(seat);
        try {
            return evaluation.get();
        } finally {
            if (before == null) {
                UNLIMITED_ASSUMED_FOR.remove();
            } else {
                UNLIMITED_ASSUMED_FOR.set(before);
            }
        }
    }
}
