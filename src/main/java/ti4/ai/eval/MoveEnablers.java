package ti4.ai.eval;

import java.util.EnumSet;
import java.util.Set;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import lombok.experimental.UtilityClass;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
public class MoveEnablers {

    public static final String LOST_STAR_CHART_PLAYED = "lostStarChart";
    public static final String NAV_SUITE_PLAYED = "navSuite";
    private static final ThreadLocal<Assumed> ASSUMED = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> LATER_ACTIVATION = new ThreadLocal<>();

    public enum Enabler {
        LOST_STAR_CHART(LOST_STAR_CHART_PLAYED, "L"),
        NAV_SUITE(NAV_SUITE_PLAYED, "N");

        private final String storedKey;
        private final String letter;

        Enabler(String storedKey, String letter) {
            this.storedKey = storedKey;
            this.letter = letter;
        }

        public String storedKey() {
            return storedKey;
        }
    }

    private record Assumed(Player seat, Set<Enabler> enablers) {}

    public static <T> T assuming(Player seat, Enabler enabler, Supplier<T> evaluation) {
        @Nullable Assumed before = ASSUMED.get();
        Set<Enabler> enablers = EnumSet.of(enabler);
        if (before != null && before.seat() == seat) enablers.addAll(before.enablers());
        ASSUMED.set(new Assumed(seat, enablers));
        try {
            return evaluation.get();
        } finally {
            if (before == null) {
                ASSUMED.remove();
            } else {
                ASSUMED.set(before);
            }
        }
    }

    public static <T> T laterActivation(Supplier<T> evaluation) {
        @Nullable Boolean before = LATER_ACTIVATION.get();
        LATER_ACTIVATION.set(Boolean.TRUE);
        try {
            return evaluation.get();
        } finally {
            if (before == null) {
                LATER_ACTIVATION.remove();
            } else {
                LATER_ACTIVATION.set(before);
            }
        }
    }

    public static boolean inLaterActivation() {
        return Boolean.TRUE.equals(LATER_ACTIVATION.get());
    }

    public static boolean assumed(Player seat, Enabler enabler) {
        @Nullable Assumed assumed = ASSUMED.get();
        return assumed != null && assumed.seat() == seat && assumed.enablers().contains(enabler);
    }

    public static boolean active(Game game, Player seat, Enabler enabler) {
        if (inLaterActivation()) return false;
        return assumed(seat, enabler) || played(game, seat, enabler);
    }

    public static boolean played(Game game, Player seat, Enabler enabler) {
        return seat.getFaction().equals(game.getStoredValue(enabler.storedKey()))
                && TurnState.isActive(game, seat)
                && TurnState.activatedThisTurn(game, seat);
    }

    public static String key(Game game, Player seat) {
        StringBuilder key = new StringBuilder();
        for (Enabler enabler : Enabler.values()) {
            if (active(game, seat, enabler)) key.append(enabler.letter);
        }
        return key.toString();
    }

    public static String assumedKey() {
        @Nullable Assumed assumed = ASSUMED.get();
        if (assumed == null || inLaterActivation()) return "";
        StringBuilder key = new StringBuilder(assumed.seat().getFaction());
        assumed.enablers().forEach(enabler -> key.append(enabler.letter));
        return key.toString();
    }
}
