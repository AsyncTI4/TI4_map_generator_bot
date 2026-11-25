package ti4.ai.eval;

import javax.annotation.Nullable;
import ti4.game.Tile;

public record Journey(
        int legs, boolean firstLegThisRound, @Nullable Tile staging, boolean bestStaging) {

    public Journey(int legs, boolean firstLegThisRound, @Nullable Tile staging) {
        this(legs, firstLegThisRound, staging, false);
    }

    public static Journey thisAction() {
        return new Journey(1, true, null);
    }

    public static Journey nextRound() {
        return new Journey(1, false, null, true);
    }

    public static Journey approaching() {
        return new Journey(2, true, null, true);
    }

    public static Journey rounds(int legs) {
        return new Journey(legs, true, null);
    }

    public Journey via(Tile stagingSystem) {
        return new Journey(legs, firstLegThisRound, stagingSystem, bestStaging);
    }

    public Journey asThreat() {
        return new Journey(legs, firstLegThisRound, staging, true);
    }

    int legsFrom(boolean firstLegMovesNow) {
        return firstLegThisRound && !firstLegMovesNow ? legs - 1 : legs;
    }
}
