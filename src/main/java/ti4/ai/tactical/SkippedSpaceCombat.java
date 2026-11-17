package ti4.ai.tactical;

import java.util.function.Supplier;
import javax.annotation.Nullable;
import lombok.experimental.UtilityClass;
import ti4.game.Player;

@UtilityClass
public class SkippedSpaceCombat {

    private static final ThreadLocal<Player> ASSUMED_FOR = new ThreadLocal<>();

    public static <T> T assuming(Player seat, Supplier<T> evaluation) {
        @Nullable Player before = ASSUMED_FOR.get();
        ASSUMED_FOR.set(seat);
        try {
            return evaluation.get();
        } finally {
            if (before == null) {
                ASSUMED_FOR.remove();
            } else {
                ASSUMED_FOR.set(before);
            }
        }
    }

    static boolean assumedFor(Player seat) {
        return ASSUMED_FOR.get() == seat;
    }

    static String assumedFaction() {
        Player seat = ASSUMED_FOR.get();
        return seat == null ? "" : seat.getFaction();
    }
}
