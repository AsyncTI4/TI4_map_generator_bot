package ti4.ai.trade;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.experimental.UtilityClass;
import ti4.ai.eval.BoardView;
import ti4.ai.eval.Threats;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;

@UtilityClass
class CeasefireValue {

    private static final double DURESS_SHARE = 0.75;
    private static final double HOME_BONUS = 2.0;
    private static final double MECATOL_SHARE = 0.5;
    private static final double AVOID_FIGHT_NEIGHBOUR = 0.5;
    private static final double AVOID_FIGHT_AFAR = 0.2;
    private static final double GIVE_COST = 2.5;
    private static final double GIVE_UNDER_DURESS = 0.5;
    private static final double OFFENSE_SHARE = 0.5;
    static final double OWN_BACK = 1.0;
    private static final double MIN_THREAT = 8.0;
    private static final double DURESS_RATIO = 1.5;
    private static final int MAX_CACHED = 512;
    private static final Map<String, Cached> STAKES = new ConcurrentHashMap<>();

    private record Cached(WeakReference<Game> game, long modified, double stake) {

        boolean isFor(Game current) {
            return game.get() == current && modified == current.getLastModifiedDate();
        }
    }

    static double received(Game game, Player seat, Player owner) {
        if (owner == null) return NotesForTrade.OTHER_NOTE;
        double stake = stakeAgainst(game, seat, owner);
        if (stake > 0) return DURESS_SHARE * stake;
        return TradeLegality.neighbours(game, seat, owner) ? AVOID_FIGHT_NEIGHBOUR : AVOID_FIGHT_AFAR;
    }

    static double givenCost(Game game, Player seat, Player receiver) {
        if (underDuress(game, seat, receiver)) return GIVE_UNDER_DURESS;
        return Math.max(GIVE_COST, OFFENSE_SHARE * stakeAgainst(game, receiver, seat));
    }

    static boolean underDuress(Game game, Player seat, Player attacker) {
        return stakeAgainst(game, seat, attacker) > 0;
    }

    static double stakeAgainst(Game game, Player defender, Player attacker) {
        String key = game.getName() + "|" + defender.getFaction() + "|" + attacker.getFaction();
        Cached cached = STAKES.get(key);
        if (cached != null && cached.isFor(game)) return cached.stake();
        double stake = Math.max(homeStake(game, defender, attacker), mecatolStake(game, defender, attacker));
        if (STAKES.size() > MAX_CACHED) STAKES.clear();
        STAKES.put(key, new Cached(new WeakReference<>(game), game.getLastModifiedDate(), stake));
        return stake;
    }

    private static double homeStake(Game game, Player defender, Player attacker) {
        Tile home = defender.getHomeSystemTile();
        if (home == null || !outgunned(game, defender, attacker, home)) return 0;
        return Desperation.vpWorth(game, defender) + HOME_BONUS;
    }

    private static double mecatolStake(Game game, Player defender, Player attacker) {
        Tile mecatol = game.getMecatolTile();
        if (mecatol == null || !defender.controlsMecatol(false) || !outgunned(game, defender, attacker, mecatol)) {
            return 0;
        }
        return MECATOL_SHARE * Desperation.vpWorth(game, defender);
    }

    private static boolean outgunned(Game game, Player defender, Player attacker, Tile tile) {
        double incoming = Threats.incomingFleetCost(game, attacker, tile);
        double defence = BoardView.shipCost(BoardView.space(tile), defender);
        return incoming >= Math.max(MIN_THREAT, DURESS_RATIO * defence);
    }
}
