package ti4.ai.tactical;

import ti4.ai.scoring.ObjectiveValue;
import ti4.ai.tactical.DefenseOutlook.Chances;
import ti4.ai.tactical.IncomingForces.IncomingForce;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;

public final class NullificationOutlook {

    private static final double TRADE_CREDIT = 0.5;
    private static final double DENIAL_SHARE = 0.5;
    private static final double LIKELY = 0.5;
    private static final Verdict NOTHING_AT_STAKE = new Verdict(0, false);

    public record Verdict(double worth, boolean scoringAtStake) {}

    private NullificationOutlook() {}

    public static Verdict endingTheTurn(Game game, Player seat, Player active, Tile tile) {
        DefenseOutlook defense = new DefenseOutlook(game, seat);
        IncomingForce force = defense.activatingForce(active, tile);
        if (force.isEmpty()) return NOTHING_AT_STAKE;
        Defenders defenders = defense.defendersAt(tile);
        Chances chances = defense.chances(tile, force, defenders);
        double harm = defense.expectedShipLoss(tile, force, defenders)
                + chances.space() * defense.shipLoss(tile)
                + chances.take() * defense.stake(tile)
                - TRADE_CREDIT * defense.expectedAttackerLoss(tile, force, defenders);
        double theirGain = chances.space() * Math.max(0, defense.spaceGain(tile, force))
                + chances.take() * Math.max(0, defense.publicGain(active, tile, force));
        double denied = DENIAL_SHARE * defense.standings().denialWeight(active) * theirGain;
        boolean scoring =
                breaksOwnScoring(defense, tile, chances) || letsThemScore(defense, active, tile, force, chances);
        return new Verdict(Math.max(0, harm) + denied, scoring);
    }

    private static boolean breaksOwnScoring(DefenseOutlook defense, Tile tile, Chances chances) {
        double shipObjectives = defense.shipLoss(tile);
        double planetObjectives = defense.scoringLoss(tile) - shipObjectives;
        return (chances.space() >= LIKELY && shipObjectives > 0) || (chances.take() >= LIKELY && planetObjectives > 0);
    }

    private static boolean letsThemScore(
            DefenseOutlook defense, Player active, Tile tile, IncomingForce force, Chances chances) {
        double fromShips = defense.spaceGain(tile, force);
        double fromPlanets = defense.publicGain(active, tile, force);
        return (chances.space() >= LIKELY && fromShips >= ObjectiveValue.VICTORY_POINT_VALUE)
                || (chances.take() >= LIKELY && fromPlanets >= ObjectiveValue.VICTORY_POINT_VALUE);
    }
}
