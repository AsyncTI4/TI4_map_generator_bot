package ti4.ai.actioncards;

import lombok.experimental.UtilityClass;
import ti4.ai.scoring.ScoringStakes;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
class TableStanding {

    static final double CONTENDER = 3.0;
    static final double LEADING = 1.3;
    static final double TRAILING = 0.6;
    static final double EVEN = 1.0;
    static final int TRAILING_GAP = 2;

    static double of(Game game, Player seat, Player other) {
        int points = other.getTotalVictoryPoints();
        if (ScoringStakes.contender(game, seat, other)) return CONTENDER;
        if (aloneInTheLead(game, other) && points > seat.getTotalVictoryPoints()) return LEADING;
        if (points + TRAILING_GAP <= seat.getTotalVictoryPoints()) return TRAILING;
        return EVEN;
    }

    static boolean aloneInTheLead(Game game, Player player) {
        int points = player.getTotalVictoryPoints();
        return points > 0
                && game.getRealPlayers().stream()
                        .filter(other -> other != player)
                        .allMatch(other -> other.getTotalVictoryPoints() < points);
    }
}
