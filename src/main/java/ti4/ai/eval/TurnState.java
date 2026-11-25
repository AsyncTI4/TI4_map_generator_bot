package ti4.ai.eval;

import javax.annotation.Nullable;
import lombok.experimental.UtilityClass;
import ti4.ai.brain.StrategyCard;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.model.StrategyCardModel;

@UtilityClass
public class TurnState {

    private static final String ACTION_SUMMARY = "currentActionSummary";
    private static final String ACTIVATED = " Activated ";
    private static final String THUNDERS_EDGE_WARFARE = "te6warfare";

    public static boolean activatedThisTurn(Game game, Player seat) {
        return game.getStoredValue(ACTION_SUMMARY + seat.getFaction()).contains(ACTIVATED);
    }

    public static boolean isActive(Game game, Player seat) {
        return seat.getUserID() != null && seat.getUserID().equals(game.getActivePlayerID());
    }

    public static boolean inOwnActivation(Game game, Player seat, @Nullable Tile target) {
        return target != null
                && isActive(game, seat)
                && activatedThisTurn(game, seat)
                && target.getPosition().equals(game.getCurrentActiveSystem());
    }

    public static boolean canActivateAnotherSystem(Game game, Player seat) {
        if (seat.isPassed()) return false;
        return seat.getTacticalCC() > 0 || holdsUnplayedThundersEdgeWarfare(game, seat);
    }

    public static boolean canActivate(Game game, Player seat, Tile target) {
        if (inOwnActivation(game, seat, target)) return true;
        if (seat.isPassed()) return false;
        if (holdsUnplayedThundersEdgeWarfare(game, seat)) return true;
        return seat.getTacticalCC() > 0 && !target.hasPlayerCC(seat);
    }

    private static boolean holdsUnplayedThundersEdgeWarfare(Game game, Player seat) {
        return seat.getSCs().stream()
                .filter(initiative -> StrategyCard.of(game, initiative) == StrategyCard.WARFARE)
                .filter(initiative -> !game.getPlayedSCs().contains(initiative))
                .anyMatch(initiative -> game.getStrategyCardModelByInitiative(initiative)
                        .map(StrategyCardModel::getBotSCAutomationID)
                        .filter(THUNDERS_EDGE_WARFARE::equalsIgnoreCase)
                        .isPresent());
    }
}
