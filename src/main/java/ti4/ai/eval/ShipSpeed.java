package ti4.ai.eval;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import lombok.experimental.UtilityClass;
import ti4.ai.brain.TickMemo;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.Constants;
import ti4.helpers.FoWHelper;
import ti4.helpers.Units.UnitType;
import ti4.model.UnitModel;

@UtilityClass
public class ShipSpeed {

    public static final String FLANK_SPEED_BOOST = "flankspeedBoost";
    private static final int NEBULA_MOVE = 1;
    private static final String SLIPSTREAM = "slipstream";
    private static final String AL_RAITH_IX_IANOVAR = "cabalbt";
    private static final String RESONANCE_GENERATOR = "crimsonbt";
    private static final String AETHERSTREAM = "as";
    private static final String PRINTED_MEMO = "speed.printed";
    private static final String ORIGIN_MEMO = "speed.origin";
    private static final String TARGET_MEMO = "speed.target";

    public static int printed(Game game, Player seat, UnitType type) {
        Map<String, Integer> memo = TickMemo.mapFor(game, PRINTED_MEMO);
        return memo.computeIfAbsent(seat.getFaction() + "#" + type, key -> printedMove(seat, type));
    }

    private static int printedMove(Player seat, UnitType type) {
        UnitModel model = seat.getUnitByType(type);
        return model == null ? 0 : Math.max(0, model.getMoveValue());
    }

    public static int base(Game game, Player seat, UnitType type, Tile origin) {
        int printed = printed(game, seat, type);
        if (printed == 0) return 0;
        return AnomalyRules.startsCapped(game, seat, origin) ? Math.min(printed, NEBULA_MOVE) : printed;
    }

    public static int of(Game game, Player seat, UnitType type, Tile origin, @Nullable Tile target) {
        int base = base(game, seat, type, origin);
        return base == 0 ? 0 : base + fleetBonus(game, seat, origin, target);
    }

    private static int fleetBonus(Game game, Player seat, Tile origin, @Nullable Tile target) {
        return originBonus(game, seat, origin) + targetBonus(game, seat, target);
    }

    public static int originBonus(Game game, Player seat, Tile origin) {
        Map<String, Integer> memo = TickMemo.mapFor(game, ORIGIN_MEMO);
        return memo.computeIfAbsent(
                seat.getFaction() + "#" + origin.getPosition(), key -> boostsLeaving(game, seat, origin));
    }

    public static int targetBonus(Game game, Player seat, @Nullable Tile target) {
        return arrivalBonus(game, seat, target) + (flankSpeed(game, seat, target) ? 1 : 0);
    }

    public static int arrivalBonus(Game game, Player seat, @Nullable Tile target) {
        if (target == null) return 0;
        Map<String, Integer> memo = TickMemo.mapFor(game, TARGET_MEMO);
        return memo.computeIfAbsent(
                seat.getFaction() + "#" + target.getPosition() + "#" + MoveEnablers.key(game, seat),
                key -> boostsArriving(game, seat, target));
    }

    public static int fleetBonusCeiling(Game game, Player seat, Tile origin) {
        return originBonus(game, seat, origin)
                + arrivalBonusCeiling(game, seat)
                + (flankSpeedPossible(game, seat) ? 1 : 0);
    }

    public static boolean flankSpeed(Game game, Player seat, @Nullable Tile target) {
        return MoveBoost.extra(seat) > 0 || flankSpeedPlayed(game, seat, target);
    }

    public static boolean flankSpeedPlayed(Game game, Player seat, @Nullable Tile target) {
        return !game.getStoredValue(FLANK_SPEED_BOOST).isEmpty() && TurnState.inOwnActivation(game, seat, target);
    }

    public static Set<UnitType> paceSetters(Game game, Player seat) {
        Set<UnitType> setters = EnumSet.copyOf(BoardView.MOVING_SHIPS);
        if (printed(game, seat, UnitType.Fighter) > 0) setters.add(UnitType.Fighter);
        return setters;
    }

    private static boolean flankSpeedPossible(Game game, Player seat) {
        if (MoveBoost.extra(seat) > 0) return true;
        return !game.getStoredValue(FLANK_SPEED_BOOST).isEmpty()
                && TurnState.isActive(game, seat)
                && TurnState.activatedThisTurn(game, seat);
    }

    public static int arrivalBonusCeiling(Game game, Player seat) {
        return (seat.hasTech(AETHERSTREAM) ? 1 : 0) + (game.isCallOfTheVoidMode() ? 1 : 0);
    }

    private static int boostsLeaving(Game game, Player seat, Tile origin) {
        int bonus = 0;
        if (slipstream(game, seat, origin)) bonus++;
        if (seat.hasUnlockedBreakthrough(AL_RAITH_IX_IANOVAR) && origin.isFracture()) bonus++;
        if (resonanceGenerator(seat, origin)) bonus++;
        return bonus;
    }

    private static boolean slipstream(Game game, Player seat, Tile origin) {
        if (!seat.hasAbility(SLIPSTREAM)) return false;
        return isHome(seat, origin) || FoWHelper.doesTileHaveAlphaOrBeta(game, origin.getPosition());
    }

    private static boolean resonanceGenerator(Player seat, Tile origin) {
        if (!seat.hasUnlockedBreakthrough(RESONANCE_GENERATOR)) return false;
        return isHome(seat, origin) || holdsActiveBreach(origin);
    }

    private static boolean holdsActiveBreach(Tile origin) {
        UnitHolder space = BoardView.space(origin);
        return space != null && space.getTokenList().contains(Constants.TOKEN_BREACH_ACTIVE);
    }

    private static boolean isHome(Player seat, Tile origin) {
        Tile home = seat.getHomeSystemTile();
        return home != null && home.getPosition().equals(origin.getPosition());
    }

    private static int boostsArriving(Game game, Player seat, Tile target) {
        int bonus = 0;
        if (seat.hasTech(AETHERSTREAM) && besideAnomaly(game, seat, target)) bonus++;
        if (game.isCallOfTheVoidMode() && target.isFracture()) bonus++;
        return bonus;
    }

    private static boolean besideAnomaly(Game game, Player seat, Tile target) {
        for (String position : Adjacency.forMovement(game, seat, target.getPosition())) {
            if (position.equals(target.getPosition())) continue;
            Tile neighbour = game.getTileByPosition(position);
            if (neighbour != null && neighbour.isAnomaly(game, seat)) return true;
        }
        return false;
    }
}
