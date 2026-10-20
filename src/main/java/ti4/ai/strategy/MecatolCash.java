package ti4.ai.strategy;

import lombok.experimental.UtilityClass;
import ti4.ai.scoring.ScoringStakes;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
public class MecatolCash {

    public static int points(Game game, Player seat) {
        if (seat.controlsMecatol(true)) return 0;
        int ownImperial = ScoringStakes.holdsUnplayedImperial(game, seat) ? 1 : 0;
        return ownImperial + overrulePoint(game, seat);
    }

    public static int overrulePoint(Game game, Player seat) {
        boolean cashable = !seat.controlsMecatol(true)
                && OverruleChoice.canOverrule(game, seat)
                && ScoringStakes.lateGame(game)
                && ScoringStakes.imperialIsUnplayed(game);
        return cashable ? 1 : 0;
    }
}
