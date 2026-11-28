package ti4.ai.tactical;

import ti4.ai.eval.Territory;
import ti4.ai.eval.Territory.Zone;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;

final class Grievance {

    static final double HOME_FACTOR = 3.0;
    static final double SLICE_FACTOR = 2.0;
    static final double CONTESTED_FACTOR = 1.0;
    static final double SCORING_DENIAL_FACTOR = 3.0;
    static final double LEADER_SCORING_DENIAL_FACTOR = 0.5;
    static final double HEAVY_LEAD_POINTS = 3.0;

    private Grievance() {}

    static double location(Game game, Tile target, Player victim) {
        return location(Territory.of(game, victim).zone(target));
    }

    static double location(Zone zone) {
        return switch (zone) {
            case HOME -> HOME_FACTOR;
            case LANE, SLICE -> SLICE_FACTOR;
            default -> CONTESTED_FACTOR;
        };
    }

    static double scoringDenial(double lead) {
        double share = Math.clamp(lead / HEAVY_LEAD_POINTS, 0.0, 1.0);
        return SCORING_DENIAL_FACTOR - (SCORING_DENIAL_FACTOR - LEADER_SCORING_DENIAL_FACTOR) * share;
    }

    static double relief(double victimThreat, double seatThreat, double ownScoringShare) {
        double pressure = Math.max(Math.max(victimThreat, seatThreat), ownScoringShare);
        return 1 - Math.clamp(pressure, 0.0, 1.0);
    }
}
