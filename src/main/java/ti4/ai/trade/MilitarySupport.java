package ti4.ai.trade;

import java.util.OptionalDouble;
import lombok.experimental.UtilityClass;
import ti4.ai.eval.GameHorizon;
import ti4.ai.promissory.NoteGiving;
import ti4.ai.scoring.ScoringPaths;
import ti4.ai.scoring.ScoringStakes;
import ti4.ai.tactical.GroundDrops;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
class MilitarySupport {

    static final String ALIAS = NoteGiving.MILITARY_SUPPORT;
    static final int PRICE = 1;
    static final double RETURN_RISK = 0.1;
    static final double IDLE_VALUE = 0.9;
    static final double PLACED_WHERE_NEEDED = 1.5;
    private static final double NEED_EPSILON = 0.05;
    static final double SPARE_INFANTRY_VALUE = 1.0;
    static final double LEAD_SHARE = 0.5;

    static boolean ownerHolds(Player owner) {
        return owner.ownsPromissoryNote(ALIAS) && NotesForTrade.inHand(owner, ALIAS);
    }

    static boolean holdsForeign(Player player) {
        return player.getPromissoryNotes().containsKey(ALIAS) && !player.ownsPromissoryNote(ALIAS);
    }

    static boolean freeToLend(Player owner) {
        return owner.getStrategicCC() == 0;
    }

    static double givenCost(Game game, Player owner, Player receiver) {
        if (!freeToLend(owner) || publicPathToWin(game, receiver)) return NotesForTrade.INFINITE;
        return RETURN_RISK + LEAD_SHARE * lead(game, owner, receiver);
    }

    static boolean publicPathToWin(Game game, Player receiver) {
        return receiver.getTotalVictoryPoints() + ScoringPaths.readyPoints(game, receiver) >= GameHorizon.goal(game);
    }

    static double lead(Game game, Player owner, Player receiver) {
        return Math.max(0, ScoringStakes.tempoLead(game, owner, receiver));
    }

    static boolean wantsInfantry(Game game, Player buyer) {
        return receivedBy(game, buyer) > PRICE;
    }

    static double receivedBy(Game game, Player buyer) {
        OptionalDouble need = needOf(game, buyer);
        if (need.isEmpty()) return 0;
        double placed = need.getAsDouble() > NEED_EPSILON ? PLACED_WHERE_NEEDED + need.getAsDouble() : IDLE_VALUE;
        double spare = Math.min(GroundDrops.INFANTRY, GroundDrops.spareShortfall(game, buyer)) * SPARE_INFANTRY_VALUE;
        return Math.max(placed, spare);
    }

    static double rivalGain(double valueToBuyer) {
        return Math.min(valueToBuyer, PRICE);
    }

    private static OptionalDouble needOf(Game game, Player buyer) {
        return GroundDrops.need(game, buyer);
    }
}
