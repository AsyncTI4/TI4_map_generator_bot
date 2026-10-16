package ti4.ai.tactical;

import ti4.ai.tactical.DefenseOutlook.Chances;
import ti4.ai.tactical.IncomingForces.IncomingForce;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;

public final class NullificationOutlook {

    private static final double TRADE_CREDIT = 0.5;
    private static final double DENIAL_SHARE = 0.5;

    private NullificationOutlook() {}

    public static double endingTheTurnIsWorth(Game game, Player seat, Player active, Tile tile) {
        DefenseOutlook defense = new DefenseOutlook(game, seat);
        IncomingForce force = defense.activatingForce(active, tile);
        if (force.isEmpty()) return 0;
        Defenders defenders = defense.defendersAt(tile);
        Chances chances = defense.chances(tile, force, defenders);
        double harm = defense.expectedShipLoss(tile, force, defenders)
                + chances.space() * defense.shipLoss(tile)
                + chances.take() * defense.stake(tile)
                - TRADE_CREDIT * defense.expectedAttackerLoss(tile, force, defenders);
        double theirGain = chances.space() * Math.max(0, defense.spaceGain(tile, force))
                + chances.take() * Math.max(0, defense.publicGain(active, tile, force));
        double denied = DENIAL_SHARE * defense.standings().denialWeight(active) * theirGain;
        return Math.max(0, harm) + denied;
    }
}
