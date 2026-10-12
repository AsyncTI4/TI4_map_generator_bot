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
        int overruledImperial = OverruleChoice.canOverrule(game, seat)
                        && ScoringStakes.lateGame(game)
                        && ScoringStakes.imperialIsUnplayed(game)
                ? 1
                : 0;
        return ownImperial + overruledImperial;
    }
}
